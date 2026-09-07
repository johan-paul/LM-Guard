package com.lmguard.exception;

/** The rule engine could not evaluate an inspection (malformed rule, missing ruleset). */
public class RuleEngineException extends ApiException {

    public RuleEngineException(String message) {
        super(ErrorCode.RULE_ENGINE_ERROR, message);
    }

    public RuleEngineException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public RuleEngineException(String message, Throwable cause) {
        super(ErrorCode.RULE_ENGINE_ERROR, message, cause);
    }
}
