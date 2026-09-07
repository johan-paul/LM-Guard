package com.lmguard.mapper;

import com.lmguard.dto.rule.RuleResponse;
import com.lmguard.entity.Rule;
import org.springframework.stereotype.Component;

@Component
public class RuleMapper {

    public RuleResponse toResponse(Rule rule) {
        if (rule == null) {
            return null;
        }
        return new RuleResponse(
                rule.getId(),
                rule.getRuleCode(),
                rule.getRuleName(),
                rule.getDescription(),
                rule.getFieldName(),
                rule.getRuleType(),
                rule.getRuleDefinition(),
                rule.getSeverity(),
                rule.getVersion(),
                rule.isActive(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
