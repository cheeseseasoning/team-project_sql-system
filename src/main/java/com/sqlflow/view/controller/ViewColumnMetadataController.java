package com.sqlflow.view.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.view.service.ViewColumnMetadataService;
import com.sqlflow.view.service.ViewColumnMetadataService.ColumnMetadata;
import com.sqlflow.view.service.ViewColumnMetadataService.MetadataResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/views/column-metadata")
@RequiredArgsConstructor
public class ViewColumnMetadataController {

    private final ViewColumnMetadataService metadata;

    @GetMapping
    public MetadataResponse read(@RequestParam("schemaName") String schema,
            @RequestParam("objectName") String view, @AuthenticationPrincipal SqlFlowUserDetails user) {
        return metadata.read(schema, view, user == null ? null : user.getUserId());
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@RequestParam("schemaName") String schema, @RequestParam("objectName") String view,
            @AuthenticationPrincipal SqlFlowUserDetails user, @RequestBody List<ColumnMetadata> entries) {
        metadata.save(schema, view, user == null ? null : user.getUserId(), entries);
    }
}
