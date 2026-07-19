package com.autoapi.controlplane.managementauth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BuiltInRoleMatrixTest {

  @Test
  void organizationOwnerIncludesProjectCreate() {
    assertTrue(
        BuiltInRole.ORGANIZATION_OWNER.permissions().contains(ManagementPermission.PROJECT_CREATE));
  }

  @Test
  void projectViewerIsReadOnly() {
    assertTrue(
        BuiltInRole.PROJECT_VIEWER.permissions().contains(ManagementPermission.PROJECT_READ));
    assertFalse(
        BuiltInRole.PROJECT_VIEWER.permissions().contains(ManagementPermission.PROJECT_UPDATE));
  }

  @Test
  void rolloutOperatorCannotManageCredentials() {
    assertFalse(
        BuiltInRole.ROLLOUT_OPERATOR
            .permissions()
            .contains(ManagementPermission.CREDENTIAL_CREATE));
  }
}
