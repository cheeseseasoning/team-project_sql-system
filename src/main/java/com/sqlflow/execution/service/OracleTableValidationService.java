package com.sqlflow.execution.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.OracleObjectName;
import com.sqlflow.execution.domain.OracleSynonymMetadata;
import com.sqlflow.execution.mapper.OracleMetadataMapper;

import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.statement.select.Select;

/**
 * SQL의 참조 이름을 Oracle의 실제 테이블·뷰로 연결한다.
 * 컬럼 유효성, SELECT 내부 안전성 및 사용자 접근 권한은 별도 검사한다.
 */
@Service
@RequiredArgsConstructor
public class OracleTableValidationService {

    // 순환하지 않더라도 지나치게 긴 동의어 연결은 제한한다.
    private static final int MAX_RESOLUTION_STEPS = 16;

    private final SqlTableExtractor sqlTableExtractor;
    private final OracleObjectNameResolver oracleObjectNameResolver;
    private final OracleMetadataMapper oracleMetadataMapper;

    /**
     * SQL에 적힌 참조 이름과 실제 대상의 대응 관계를 반환한다.
     * 예: DUAL → OracleObjectName("SYS", "DUAL")
     */
    @Transactional(readOnly = true)
    public Map<String, OracleObjectName> validateTables(Select select) {

        Set<String> extractedNames = sqlTableExtractor.extractTables(select);
        String currentSchema = oracleMetadataMapper.selectCurrentSchema();

        Map<String, OracleObjectName> resolvedObjects = new HashMap<>();

        for (String extractedName : extractedNames) {
            OracleObjectName name = oracleObjectNameResolver.resolve(extractedName, currentSchema);
            boolean allowPublicSynonym = !oracleObjectNameResolver.hasExplicitSchema(extractedName);

            OracleObjectName resolved = resolveObject(name, allowPublicSynonym);
            resolvedObjects.put(extractedName, resolved);
        }

        return Map.copyOf(resolvedObjects);
    }

    /** 실제 객체를 찾거나, 동의어의 대상 이름을 따라가며 확인한다. */
    private OracleObjectName resolveObject(OracleObjectName name, boolean allowPublicSynonym) {

        OracleObjectName current = name;
        Set<OracleObjectName> visited = new HashSet<>();

        for (int step = 0; step < MAX_RESOLUTION_STEPS; step++) {

            // A → B → A처럼 이미 확인한 대상으로 돌아오는 연결을 차단한다.
            if (!visited.add(current)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "동의어가 순환 참조되어 대상을 확인할 수 없습니다.");
            }

            int objectCount = oracleMetadataMapper.countSelectableObjects(current.owner(), current.objectName());

            if (objectCount > 0) {
                return current;
            }

            OracleSynonymMetadata synonym = oracleMetadataMapper.selectSynonym(current.owner(), current.objectName());

            // 최초 이름에 스키마가 없고 해당 스키마의 동의어도 없을 때만 공용 동의어를 찾는다.
            if (synonym == null && allowPublicSynonym) {
                synonym = oracleMetadataMapper.selectSynonym("PUBLIC", current.objectName());
            }

            // 이후에는 동의어가 명시한 대상 스키마를 기준으로 조회한다.
            allowPublicSynonym = false;

            if (synonym == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 대상을 찾을 수 없거나 DB 계정에서 확인할 수 없습니다.");
            }

            if (synonym.getDbLink() != null && !synonym.getDbLink().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "원격 DB 링크를 사용하는 조회 대상은 지원하지 않습니다.");
            }

            if (synonym.getTableOwner() == null || synonym.getTableName() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "동의어의 대상 정보가 올바르지 않습니다.");
            }

            // 데이터 사전에서 받은 이름이므로 대소문자를 다시 변환하지 않는다.
            current = new OracleObjectName(synonym.getTableOwner(), synonym.getTableName());
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "동의어 연결이 허용된 확인 횟수를 초과했습니다.");
    }
}