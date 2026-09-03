package io.multiagent.audit.mapper;

import org.mapstruct.Mapper;

import io.multiagent.audit.dto.AuditEventDTO;
import io.multiagent.audit.dto.AuditForwardEventDTO;

@Mapper(componentModel = "spring")
public interface AuditEventMapper {

    AuditForwardEventDTO toEnriched(AuditEventDTO dto);
}