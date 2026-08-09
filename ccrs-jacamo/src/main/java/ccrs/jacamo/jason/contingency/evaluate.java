package ccrs.jacamo.jason.contingency;
import ccrs.core.logging.CcrsEventLogger;
import ccrs.core.contingency.ContingencyCcrs;
import ccrs.core.contingency.dto.Situation;
import ccrs.core.contingency.dto.StrategyResult;
import ccrs.core.rdf.CcrsContext;
import ccrs.jacamo.CcrsJacamoRuntime;
import ccrs.jacamo.jason.JasonRdfAdapter;
import jason.JasonException;
import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Internal action bridging AgentSpeak and Contingency-CCRS.
 *
 * <p>The public signature is:</p>
 *
 * <pre>
 * evaluate(map(trigger(...), current(...), target(...), action(...), ...), Result)
 * </pre>
 *
 * <p>Supported keys are {@code trigger}, {@code current}, {@code target},
 * {@code action}, {@code error}, {@code http_status}, {@code error_type},
 * {@code error_message}, and {@code metadata(Key, Value)}. Callers describe
 * observations; Java strategies decide applicability from those facts.</p>
 * See contingency/README.md for usage examples.
 */
public class evaluate extends DefaultInternalAction {

    private static final Logger logger = Logger.getLogger(evaluate.class.getName());

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {

        if (args.length != 2) {
            throw new JasonException(
                "ccrs.contingency.evaluate requires 2 args: (ContextMap, Result)"
            );
        }

        ContingencyCcrs ccrs = CcrsJacamoRuntime.getOrCreateContingencyCcrs();

        // Retrieve the pre-initialized context. CcrsAgentArch normally installs
        // this, but creating a fallback keeps the internal action usable in
        // minimal Jason-only tests.
        Object ctxParam = ts.getAg().getTS().getSettings().getUserParameters().get("ccrs_context");
        CcrsContext context;
        if (ctxParam instanceof CcrsContext configuredContext) {
            context = configuredContext;
        } else {
            context = new JasonCcrsContext(ts.getAg());
            ts.getAg().getTS().getSettings().addOption("ccrs_context", context);
            logger.warning("[ContingencyCcrs] Created fallback JasonCcrsContext; CcrsAgentArch was not pre-initialized");
        }

        if (context instanceof JasonCcrsContext jCtx) {
            logger.fine("[ContingencyCcrs] JasonCcrsContext details: " + jCtx.toDebugString());
        }

        Situation situation = parseSituation(args[0]);
        
        // Track current resource in context when available
        String currentResource = situation.getCurrentResource();
        if (currentResource != null && !currentResource.isEmpty() 
            && context instanceof ccrs.jacamo.jason.contingency.JasonCcrsContext) {
            ((ccrs.jacamo.jason.contingency.JasonCcrsContext) context).setCurrentResource(currentResource);
            logger.fine("[ContingencyCcrs] Set current resource to: " + currentResource);
        }

        logger.info("[ContingencyCcrs] Evaluating situation: " + situation + " with context");
        CcrsEventLogger.info(logger, "ccrs.contingency.evaluate.request", CcrsEventLogger.fields(
            "agent_id", context.getAgentId(),
            "trigger", situation.getTrigger(),
            "current_resource", situation.getCurrentResource(),
            "target_resource", situation.getTargetResource(),
            "failed_action", situation.getFailedAction(),
            "http_status", situation.getErrorInfoString("httpStatus"),
            "error_type", situation.getErrorInfoString("errorType"),
            "error_message", situation.getErrorInfoString("message"),
            "has_history", context.hasHistory()
        ));

        // Evaluate Contingency Strategies via the default path, which also records trace history.
        List<StrategyResult> results = ccrs.evaluate(situation, context);

        logger.info("[ContingencyCcrs] Evaluation produced " + results.size() + " results.");
        logEvaluationReturned(context, results);
        
        // Inject OpportunisticResult mental notes as ccrs/3 beliefs (B2)
        injectOpportunisticNotes(ts, results);

        ListTerm resultList = buildResultList(results);
        logger.info("[ContingencyCcrs] Result list: " + resultList);
        Term out = args[args.length - 1];
        return un.unifies(out, resultList);
    }

    private void logEvaluationReturned(CcrsContext context, List<StrategyResult> results) {
        StrategyResult.Suggestion topSuggestion = null;
        int suggestionCount = 0;
        int opportunisticGuidanceCount = 0;

        for (StrategyResult result : results) {
            if (!result.isSuggestion()) {
                continue;
            }

            suggestionCount++;
            StrategyResult.Suggestion suggestion = result.asSuggestion();
            if (topSuggestion == null) {
                topSuggestion = suggestion;
            }
            if (suggestion.hasOpportunisticGuidance()) {
                opportunisticGuidanceCount++;
            }
        }

        CcrsEventLogger.info(logger, "ccrs.contingency.evaluate.returned", CcrsEventLogger.fields(
            "agent_id", context.getAgentId(),
            "result_count", results.size(),
            "suggestion_count", suggestionCount,
            "opportunistic_guidance_count", opportunisticGuidanceCount,
            "top_strategy", topSuggestion != null ? topSuggestion.getStrategyId() : null,
            "top_action", topSuggestion != null ? topSuggestion.getActionType() : null,
            "top_target", topSuggestion != null ? topSuggestion.getActionTarget() : null,
            "top_confidence", topSuggestion != null ? topSuggestion.getConfidence() : null,
            "top_has_opportunistic_guidance", topSuggestion != null && topSuggestion.hasOpportunisticGuidance()
        ));
    }

    // ------------------------------------------------------------------
    // Situation parsing
    // ------------------------------------------------------------------

    Situation parseSituation(Term contextMap) throws JasonException {
        if (!isMap(contextMap)) {
            throw new JasonException(
                "First argument must be map(trigger(...), current(...), ...)"
            );
        }

        Situation.Builder builder = Situation.builder();
        parseMapIntoBuilder(contextMap, builder);
        return builder.build();
    }

    private boolean isMap(Term t) {
        // Map structure: map(key1(val1), key2(val2), ...)
        return t.isStructure() && ((Structure) t).getFunctor().equals("map");
    }

    private void parseMapIntoBuilder(Term mapTerm, Situation.Builder builder) throws JasonException {
        if (!mapTerm.isStructure()) {
            throw new JasonException("Context map must be a map(...) structure");
        }
        Structure map = (Structure) mapTerm;
        
        for (Term entry : map.getTerms()) {
            if (!entry.isStructure()) {
                throw new JasonException("Context map entries must be key(value) structures: " + entry);
            }
            Structure kv = (Structure) entry;
            String key = kv.getFunctor();
            
            switch (key) {
                case "trigger" -> builder.trigger(singleValue(kv));
                case "current" -> builder.currentResource(singleValue(kv));
                case "target" -> builder.targetResource(singleValue(kv));
                case "action" -> builder.failedAction(singleValue(kv));
                case "error" -> {
                    requireArity(kv, 1);
                    parseErrorInfo(kv.getTerm(0), builder);
                }
                case "http_status" -> builder.errorInfo("httpStatus", singleValue(kv));
                case "error_type" -> builder.errorInfo("errorType", singleValue(kv));
                case "error_message" -> builder.errorInfo("message", singleValue(kv));
                case "metadata" -> {
                    requireArity(kv, 2);
                    builder.metadata(
                        JasonRdfAdapter.termToString(kv.getTerm(0)),
                        JasonRdfAdapter.termToString(kv.getTerm(1)));
                }
                default -> throw new JasonException("Unsupported contingency context key: " + key);
                }
        }
    }

    private String singleValue(Structure entry) throws JasonException {
        requireArity(entry, 1);
        return JasonRdfAdapter.termToString(entry.getTerm(0));
    }

    private void requireArity(Structure entry, int expectedArity) throws JasonException {
        if (entry.getArity() != expectedArity) {
            throw new JasonException(
                entry.getFunctor() + " requires exactly " + expectedArity
                    + (expectedArity == 1 ? " value" : " values") + ": " + entry
            );
        }
    }

    private void parseErrorInfo(Term t, Situation.Builder builder) {
        if (t == null) return;
        String error = JasonRdfAdapter.termToString(t);
        if (error.isEmpty() || error.equals("null")) return;
        
        // Try to parse as HTTP status code
        if (error.matches("\\d{3}")) {
            builder.httpError(Integer.parseInt(error), "HTTP " + error);
        } else {
            builder.errorInfo("message", error);
        }
    }

    // ------------------------------------------------------------------
    // Result conversion
    // ------------------------------------------------------------------

    private ListTerm buildResultList(List<StrategyResult> results) {

        ListTerm list = new ListTermImpl();
        ListTerm tail = list;

        for (StrategyResult r : results) {
            if (!r.isSuggestion()) continue;

            StrategyResult.Suggestion s = r.asSuggestion();

            Structure sug = ASSyntax.createStructure(
                "suggestion",
                ASSyntax.createString(s.getStrategyId()),
                ASSyntax.createString(s.getActionType()),
                s.getActionTarget() != null
                    ? ASSyntax.createString(s.getActionTarget())
                    : ASSyntax.createAtom("null"),
                ASSyntax.createNumber(s.getConfidence()),
                ASSyntax.createString(
                    s.getRationale() != null ? s.getRationale() : ""
                ),
                buildParams(withSuggestionMetadata(s))
            );

            tail = tail.append(sug);
        }

        return list;
    }

    private Map<String, Object> withSuggestionMetadata(StrategyResult.Suggestion suggestion) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.putAll(suggestion.getActionParams());
        params.put("hasOpportunisticGuidance", suggestion.hasOpportunisticGuidance());
        return params;
    }

    private Term buildParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return new ListTermImpl();
        }

        ListTerm list = new ListTermImpl();
        ListTerm tail = list;

        for (Map.Entry<String, Object> e : params.entrySet()) {
            Structure pair = ASSyntax.createStructure(
                e.getKey(),
                buildValue(e.getKey(), e.getValue())
            );
            tail = tail.append(pair);
        }

        return list;
    }
    
    private Term buildValue(Object value) {
        return buildValue(null, value);
    }

    private Term buildValue(String key, Object value) {
        if (value == null) {
            return ASSyntax.createAtom("null");
        }

        if ("body".equals(key) && value instanceof String bodyText) {
            Term parsedBody = parseBodyTerm(bodyText);
            if (parsedBody != null) {
                return parsedBody;
            }
        }
    
        if (value instanceof List<?> list) {
            ListTerm jasonList = new ListTermImpl();
            ListTerm tail = jasonList;
        
            for (Object o : list) {
                Term t = buildValue(o);
                tail = tail.append(t);
            }
            return jasonList;
        }

        if (value instanceof Map<?, ?> map) {
            ListTerm jasonList = new ListTermImpl();
            ListTerm tail = jasonList;

            for (Map.Entry<?, ?> e : map.entrySet()) {
                Structure pair = ASSyntax.createStructure(
                    "entry",
                    ASSyntax.createString(String.valueOf(e.getKey())),
                    buildValue(e.getValue())
                );
                tail = tail.append(pair);
            }
            return jasonList;
        }
    
        if (value instanceof Number n) {
            return ASSyntax.createNumber(n.doubleValue());
        }

        if (value instanceof Boolean b) {
            return ASSyntax.createAtom(b ? "true" : "false");
        }
    
        return ASSyntax.createString(String.valueOf(value));
    }

    private Term parseBodyTerm(String bodyText) {
        if (bodyText == null) {
            return null;
        }

        String trimmed = bodyText.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
            return null;
        }

        Term agentSpeakBody = parseAgentSpeakBody(trimmed);
        if (agentSpeakBody != null) {
            return agentSpeakBody;
        }

        Term simpleTurtleBody = parseTurtleTripleBody(trimmed);
        if (simpleTurtleBody != null) {
            return simpleTurtleBody;
        }

        return parseTurtleBody(trimmed);
    }

    private Term parseAgentSpeakBody(String bodyText) {
        if (!looksLikeAgentSpeakBody(bodyText)) {
            return null;
        }

        try {
            return ASSyntax.parseTerm(bodyText);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to parse LLM body as AgentSpeak term: " + bodyText, e);
            return null;
        }
    }

    private boolean looksLikeAgentSpeakBody(String bodyText) {
        return bodyText.startsWith("[") || bodyText.startsWith("rdf(");
    }

    private Term parseTurtleTripleBody(String bodyText) {
        Pattern triplePattern = Pattern.compile(
            "^\\s*<([^>]+)>\\s+<([^>]+)>\\s+(?:<([^>]+)>|\\\"((?:\\\\.|[^\\\"])*)\\\")\\s*\\.\\s*$",
            Pattern.DOTALL
        );
        Matcher matcher = triplePattern.matcher(bodyText);
        if (!matcher.matches()) {
            return null;
        }

        String subject = matcher.group(1);
        String predicate = matcher.group(2);
        String uriObject = matcher.group(3);
        String literalObject = matcher.group(4);
        String object = uriObject != null ? uriObject : unescapeTurtleLiteral(literalObject);
        String objectType = uriObject != null ? "uri" : "literal";

        String agentSpeak = String.format(
            "[rdf(\"%s\",\"%s\",\"%s\")[rdf_type_map(uri,uri,%s)]]",
            escapeAgentSpeakString(subject),
            escapeAgentSpeakString(predicate),
            escapeAgentSpeakString(object),
            objectType
        );

        try {
            return ASSyntax.parseTerm(agentSpeak);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to parse converted Turtle LLM body: " + bodyText, e);
            return null;
        }
    }

    private Term parseTurtleBody(String bodyText) {
        try {
            Model model = ModelFactory.createDefaultModel();
            model.read(new StringReader(bodyText), null, "TURTLE");

            ListTerm body = new ListTermImpl();
            ListTerm tail = body;
            StmtIterator statements = model.listStatements();
            try {
                while (statements.hasNext()) {
                    Term rdfTerm = buildRdfTerm(statements.nextStatement());
                    if (rdfTerm != null) {
                        tail = tail.append(rdfTerm);
                    }
                }
            } finally {
                statements.close();
            }

            return body.isEmpty() ? null : body;
        } catch (Exception e) {
            logger.log(Level.WARNING, "LLM body is not parseable Turtle; forwarding as string");
            return null;
        }
    }

    private Term buildRdfTerm(Statement statement) {
        if (statement == null || !statement.getSubject().isURIResource()) {
            return null;
        }

        String subject = statement.getSubject().getURI();
        String predicate = statement.getPredicate().getURI();
        RDFNode objectNode = statement.getObject();
        String object;
        String objectType;

        if (objectNode.isURIResource()) {
            object = objectNode.asResource().getURI();
            objectType = "uri";
        } else if (objectNode.isLiteral()) {
            org.apache.jena.rdf.model.Literal literal = objectNode.asLiteral();
            object = literal.getLexicalForm();
            objectType = "literal";
        } else {
            return null;
        }

        jason.asSyntax.Literal rdf = ASSyntax.createLiteral(
            "rdf",
            ASSyntax.createString(subject),
            ASSyntax.createString(predicate),
            ASSyntax.createString(object)
        );
        rdf.addAnnot(ASSyntax.createStructure(
            "rdf_type_map",
            ASSyntax.createAtom("uri"),
            ASSyntax.createAtom("uri"),
            ASSyntax.createAtom(objectType)
        ));
        return rdf;
    }

    private String unescapeTurtleLiteral(String value) {
        if (value == null) {
            return null;
        }
        return value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t");
    }

    private String escapeAgentSpeakString(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }


    // ------------------------------------------------------------------
    // OpportunisticResult injection (B2)
    // ------------------------------------------------------------------
    
    /**
     * Inject OpportunisticResult mental notes from contingency strategies as ccrs/3 beliefs.
     * These beliefs do NOT have artifact_name annotation, so they persist across perception cycles.
     */
    private void injectOpportunisticNotes(TransitionSystem ts, List<StrategyResult> results) {
        for (StrategyResult r : results) {
            if (!r.isSuggestion()) continue;
            
            StrategyResult.Suggestion s = r.asSuggestion();
            if (!s.hasOpportunisticGuidance()) continue;
            
            for (ccrs.core.opportunistic.OpportunisticResult opp : s.getOpportunisticGuidance()) {
                try {
                    // Use JasonRdfAdapter to create consistent ccrs/3 belief structure
                    // Source "contingency" marks as contingency-generated (no artifact_name = persists)
                    Literal ccrsBelief = JasonRdfAdapter.createCcrsBelief(opp, "contingency");
                    
                    // Add to belief base (no artifact_name annotation = persists)
                    if (ts.getAg().getBB().add(ccrsBelief)) {
                        // Generate +ccrs(...) event for agent plans
                        Trigger te = new Trigger(Trigger.TEOperator.add, 
                            Trigger.TEType.belief, ccrsBelief.copy());
                        ts.getC().addEvent(new jason.asSemantics.Event(te, jason.asSemantics.Intention.EmptyInt));
                        
                        logger.fine("[ContingencyCcrs] Injected contingency mental note: " + ccrsBelief);
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to inject opportunistic note: " + opp, e);
                }
            }
        }
    }
    
}
