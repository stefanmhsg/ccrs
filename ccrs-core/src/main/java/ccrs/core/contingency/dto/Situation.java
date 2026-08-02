package ccrs.core.contingency.dto;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Describes a situation requiring contingency handling.
 * This is the primary input to contingency CCRS strategies.
 * 
 * Callers describe the observations that motivated runtime guidance. Concrete
 * strategies decide applicability from the available evidence and context.
 */
public class Situation {

    // What prompted the runtime-guidance request
    private final String trigger;
    
    // Location context
    private final String currentResource;
    private final String targetResource;
    
    // Failure details
    private final String failedAction;
    private final Map<String, Object> errorInfo;
    
    // Extensible metadata
    private final Map<String, Object> metadata;
    
    private Situation(Builder builder) {
        this.trigger = builder.trigger;
        this.currentResource = builder.currentResource;
        this.targetResource = builder.targetResource;
        this.failedAction = builder.failedAction;
        this.errorInfo = Collections.unmodifiableMap(new HashMap<>(builder.errorInfo));
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
    }
    
    // Getters
    
    public String getTrigger() {
        return trigger;
    }
    
    public String getCurrentResource() {
        return currentResource;
    }
    
    public String getTargetResource() {
        return targetResource;
    }
    
    public String getFailedAction() {
        return failedAction;
    }
    
    public Map<String, Object> getErrorInfo() {
        return errorInfo;
    }
    
    public Object getErrorInfo(String key) {
        return errorInfo.get(key);
    }
    
    public String getErrorInfoString(String key) {
        Object value = errorInfo.get(key);
        return value != null ? value.toString() : null;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Situation{");
        boolean hasField = false;
        if (trigger != null) {
            sb.append("trigger='").append(trigger).append("'");
            hasField = true;
        }
        if (currentResource != null) {
            if (hasField) sb.append(", ");
            sb.append("current='").append(currentResource).append("'");
            hasField = true;
        }
        if (targetResource != null) {
            if (hasField) sb.append(", ");
            sb.append("target='").append(targetResource).append("'");
            hasField = true;
        }
        if (failedAction != null) {
            if (hasField) sb.append(", ");
            sb.append("action='").append(failedAction).append("'");
            hasField = true;
        }
        if (!errorInfo.isEmpty()) {
            if (hasField) sb.append(", ");
            sb.append("error=").append(errorInfo);
        }
        sb.append("}");
        return sb.toString();
    }
    
    // Builder
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String trigger;
        private String currentResource;
        private String targetResource;
        private String failedAction;
        private Map<String, Object> errorInfo = new HashMap<>();
        private Map<String, Object> metadata = new HashMap<>();
        
        private Builder() {
        }
        
        public Builder trigger(String trigger) {
            this.trigger = trigger;
            return this;
        }
        
        public Builder currentResource(String currentResource) {
            this.currentResource = currentResource;
            return this;
        }
        
        public Builder targetResource(String targetResource) {
            this.targetResource = targetResource;
            return this;
        }
        
        public Builder failedAction(String failedAction) {
            this.failedAction = failedAction;
            return this;
        }
        
        public Builder errorInfo(String key, Object value) {
            this.errorInfo.put(key, value);
            return this;
        }
        
        public Builder errorInfo(Map<String, Object> errorInfo) {
            this.errorInfo.putAll(errorInfo);
            return this;
        }
        
        public Builder httpError(int statusCode, String message) {
            this.errorInfo.put("httpStatus", String.valueOf(statusCode));
            this.errorInfo.put("message", message);
            return this;
        }
        
        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }
        
        public Situation build() {
            return new Situation(this);
        }
    }
}
