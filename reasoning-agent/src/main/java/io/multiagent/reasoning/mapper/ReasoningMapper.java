package io.multiagent.reasoning.mapper;

import io.multiagent.core.model.ReasoningResult;
import io.multiagent.core.model.ExpenseItem;
import io.multiagent.reasoning.dto.ReasoningAnalysisDTO;
import io.multiagent.reasoning.dto.ExpenseItemDTO;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE) // évite warnings si tu ajoutes des champs)
public interface ReasoningMapper {

    ReasoningMapper INSTANCE = Mappers.getMapper(ReasoningMapper.class);

    // Mapping principal
    @Mapping(target = "originalText", source = "raw")
    ReasoningAnalysisDTO toDTO(ReasoningResult result);

    // Mapping ExpenseItem -> ExpenseItemDTO
    ExpenseItemDTO toDTO(ExpenseItem item);
}
