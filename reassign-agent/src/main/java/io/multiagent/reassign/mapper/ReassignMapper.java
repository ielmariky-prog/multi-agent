package io.multiagent.reassign.mapper;

import io.multiagent.core.model.AssignmentResult;
import io.multiagent.reassign.dto.AssignmentDTO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ReassignMapper {

    AssignmentDTO toDTO(AssignmentResult source);
}