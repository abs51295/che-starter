/*-
 * #%L
 * che-starter
 * %%
 * Copyright (C) 2017 Red Hat, Inc.
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */
package io.fabric8.che.starter.controller;

import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.fabric8.che.starter.che6.migration.Che6MigrationMap;
import io.fabric8.che.starter.che6.migration.Che6Migrator;
import io.fabric8.che.starter.che6.toggle.Che6Toggle;
import io.fabric8.che.starter.client.keycloak.KeycloakTokenParser;
import io.fabric8.che.starter.client.keycloak.KeycloakTokenValidator;
import io.fabric8.che.starter.model.server.CheServerInfo;
import io.fabric8.che.starter.util.CheServerHelper;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@CrossOrigin
@RestController
public class CheServerController {
    private static final Logger LOG = LoggerFactory.getLogger(CheServerController.class);

    @Autowired
    Che6Toggle che6toggle;

    @Autowired
    Che6Migrator che6Migrator;

    @Autowired
    Che6MigrationMap che6MigrationMap;

    @Autowired
    KeycloakTokenParser keycoakTokenParser;


    @ApiOperation(value = "Get Che server info")
    @GetMapping("/server")
    public CheServerInfo getCheServerInfo(@RequestParam String masterUrl, @RequestParam String namespace,
            @ApiParam(value = "Keycloak token", required = true) @RequestHeader("Authorization") String keycloakToken, HttpServletRequest request) throws Exception {
        KeycloakTokenValidator.validate(keycloakToken);

        if (che6toggle.isChe6(keycloakToken)) {
            String identityId = keycoakTokenParser.getIdentityId(keycloakToken);
            boolean isAlreadyMigrated = che6MigrationMap.get().getOrDefault(identityId, false);
            if (isAlreadyMigrated) {
                LOG.info("User '{}' have been already migrated to che 6", identityId);
                return getCheServerInfo(request, true);
            } else {
                LOG.info("User '{}' is not yet migrated to che 6. Need to migrate now", identityId);
                che6Migrator.migrateWorkspaces(keycloakToken, namespace);
                delayResponse();
                return getCheServerInfo(request, false);
            }
        }
        return getCheServerInfo(request, true);
    }

    /*
     * Deprecated since che-starter is not supposed to start multi-tenant che server which never idles
     */
    @Deprecated
    @ApiOperation(value = "Start Che Server")
    @PatchMapping("/server")
    public CheServerInfo startCheServer(@RequestParam String masterUrl, @RequestParam String namespace,
            @ApiParam(value = "Keycloak token", required = true) @RequestHeader("Authorization") String keycloakToken, HttpServletResponse response, HttpServletRequest request) throws Exception {

        KeycloakTokenValidator.validate(keycloakToken);

        if (che6toggle.isChe6(keycloakToken)) {
            String identityId = keycoakTokenParser.getIdentityId(keycloakToken);
            Boolean isReady = che6MigrationMap.get().getOrDefault(identityId, false);
            LOG.info("User '{}' workspaces have been migrated: {}", identityId, isReady);
            return getCheServerInfo(request, isReady);
        }

        return getCheServerInfo(request, true);
    }

    private CheServerInfo getCheServerInfo(HttpServletRequest request, boolean isReady) {
        String requestURL = request.getRequestURL().toString();
        return CheServerHelper.generateCheServerInfo(isReady, requestURL, true);
    }

    private void delayResponse() {
        try {
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException e) {
        }
    }

}
