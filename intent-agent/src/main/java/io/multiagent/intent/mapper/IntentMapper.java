package io.multiagent.intent.mapper;

import io.multiagent.core.model.IntentResult;      // modèle AI-Core
import io.multiagent.intent.dto.IntentDTO;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface IntentMapper {

    IntentDTO toDTO(IntentResult source);
}