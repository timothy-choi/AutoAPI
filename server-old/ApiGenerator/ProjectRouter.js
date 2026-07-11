const express = require('express');
const router = express.Router();
const ModelController = require('./ModelController');
const DatabaseController = require('./DatabaseController');

router.get("/Model/:modelId", ModelController.GetModelById);

router.get("/Model/name/:modelName", ModelController.GetModelByName);

router.post("/Model", ModelController.CreateModel);

router.put("/Model/:modelId/Version/:version", ModelController.EditVersion);

router.put("/Model/:modelId/ModelAttribute/add/:username", ModelController.AddModelAttribute);

router.put("/Model/:modelId/ModelAttribute/remove/:modelAttributeId/:username", ModelController.DeleteModelAttribute);

router.put("/Model/:modelId/ModelAttribute/edit/:modelAttributeId/:username", ModelController.EditModelAttribute);

router.put("/Model/:modelId/ModelDescription", ModelController.EditModelDescription);

router.put("/Model/:modelId/ModelCreationFile", ModelController.SetModelCreationFile);

router.put("/Model/:modelId/ModelDatabaseInfo", ModelController.SetModelDatabaseInfo);

router.put("/Model/:modelId/ModelChangelogInfo", ModelController.AddModelChangelog);

router.delete("/Model/:modelId", ModelController.DeleteModel);


router.get("/Database/:databaseId", DatabaseController.getDatabaseById);

router.get("/Database/name/:databaseName", DatabaseController.getDatabaseByName);

router.post("/Database", DatabaseController.createDatabase);

router.put("/Database/:databaseId/Description/:username", DatabaseController.editDatabaseDescription);

router.put("/Database/:databaseId/ModelsUsed/add/:model/:username", DatabaseController.addModelsUsed);

router.put("/Database/:databaseId/ModelsUsed/remove/:model/:username", DatabaseController.removeModelsUsed);

router.put("/Database/:databaseId/ModelTableInfo/add/:username", DatabaseController.addModelTableInfo);

router.put("/Database/:databaseId/ModelTableInfo/remove/:username", DatabaseController.removeModelTableInfo);

router.put("/Database/:databaseId/ModelTableInfo/edit/:modelTableInfoId/:username", DatabaseController.editModelTableInfo);

router.put("/Database/:databaseId/DatabaseInstanceInfo/:username", DatabaseController.modifyDatabaseInstanceInfo);

router.put("/Database/:databaseId/HealthStatus/:healthStatus/:username", DatabaseController.setHealthStatus);

router.put("/Database/:databaseId/Status/:status/:username", DatabaseController.setStatus);

router.put("/Database/:databaseId/DatabaseChangeLog/:username", DatabaseController.addDatabaseChangeLog);

router.put("/Database/:databaseId/DatabaseOperationsLog/:username", DatabaseController.addDatabaseOperationsLog)

router.put("/Database/:databaseId/DatabaseBackupInfo/:username", DatabaseController.modifyDatabaseBackupInfo);

router.put("/Database/:databaseId/DatabaseVersionHistory/:username", DatabaseController.addDatabaseVersionHistory);

router.put("/Database/:databaseId/DatabaseCloudInfo/:username", DatabaseController.modifyDatabaseCloudInfo);

router.put("/Database/:databaseId/ServerlessFunction/add/:username", DatabaseController.addServerlessFunction);

router.put("/Database/:databaseId/ServerlessFunction/remove/:username", DatabaseController.removeServerlessFunction);

router.put("/Database/:databaseId/ServerlessFunction/edit/:serverlessFunctionId/:username", DatabaseController.editServerlessFunction);

router.put("/Database/:databaseId/DatabaseUsageInfo/:username", DatabaseController.editDatabaseUsageInfo);

router.put("/Database/:databaseId/DatabaseBillingInfo/:username", DatabaseController.editDatabaseBillingInfo);

router.put("/Database/:databaseId/StartedAt", DatabaseController.setDatabaseStartTime);

router.delete("/Database/:databaseId", DatabaseController.deleteDatabase);


router.get("/Endpoints/:endpointId", EndpointControllerGgetEndpointsById);

router.get("/Endpoints/name/:projectName", EndpointController.GetEndpointsByName);

router.post("/Endpoints", EndpointController.CreateEndpoints);

router.put("/Endpoints/endpointHeader/add/:endpointId/:username", EndpointController.AddEndpointHeader);

router.put("/Endpoints/endpointHeader/remove/:endpointId/:endpointHeaderId/:username", EndpointController.RemoveEndpointHeader);

router.put("/Endpoints/endpointHeader/edit/:endpointId/:endpointHeaderId/:username", EndpointController.EditEndpointHeader);

router.put("/Endpoints/model/add/:endpointId/:username", EndpointController.AddEndpointModel);

router.put("/Endpoints/model/remove/:endpointId/:endpointModelId/:username", EndpointController.RemoveEndpointModel);

router.put("/Endpoints/model/edit/:endpointId/:endpointModelId/:username", EndpointController.EditEndpointModel);

router.put("/Endpoints/database/add/:endpointId/:username", EndpointController.AddEndpointDatabase);

router.put("/Endpoints/database/remove/:endpointId/:endpointDatabaseId/:username", EndpointController.RemoveEndpointDatabase);

router.put("/Endpoints/database/edit/:endpointId/:endpointDatabaseId/:username", EndpointController.EditEndpointDatabase);

router.put("/Endpoints/serverlessFunction/add/:endpointId/:username", EndpointController.AddEndpointServerlessFunction);

router.put("/Endpoints/serverlessFunction/remove/:endpointId/:endpointServerlessFunctionId/:username", EndpointController.RemoveEndpointServerlessFunction);

router.put("/Endpoints/serverlessFunction/edit/:endpointId/:endpointServerlessFunctionId/:username", EndpointController.EditEndpointServerlessFunction);

router.put("/Endpoints/endpointCreationFile/:endpointId/:username", EndpointController.ModifyEndpointCreationFile);

router.put("/Endpoints/endpointRequestInfo/add/:endpointId/:username", EndpointController.AddEndpointRequestInfo);

router.put("/Endpoints/endpointRequestInfo/remove/:endpointId/:endpointRequestInfoId/:username", EndpointController.RemoveEndpointRequestInfo);

router.put("/Endpoints/endpointRequestInfo/edit/:endpointId/:endpointRequestInfoId/:username", EndpointController.EditEndpointRequestInfo);

router.put("/Endpoints/EndpointImplementationInfo/add/:endpointId/:username", EndpointController.AddEndpointImplementationInfo);

router.put("/Endpoints/EndpointImplementationInfo/remove/:endpointId/:endpointImplementationInfoId/:username", EndpointController.RemoveEndpointImplementationInfo);

router.put("/Endpoints/EndpointImplementationInfo/edit/:endpointId/:endpointImplementationInfoId/:username", EndpointController.EditEndpointImplementationInfo);

router.put("/Endpoints/EndpointsStatus/add/:endpointId/:username", EndpointController.AddApiEndpointsStatus);

router.put("/Endpoints/EndpointsStatus/edit/:endpointId/:endpointStatusId/:username", EndpointController.EditApiEndpointsStatus);

router.put("/Endpoints/endpointsDescription/:endpointId/:username", EndpointController.ModifyEndpointDescription);

router.put("/Endpoints/responseSchema/add/:endpointId/:username", EndpointController.AddEndpointResponseSchema);

router.put("/Endpoints/responseSchema/remove/:endpointId/:endpointResponseSchemaId/:username", EndpointController.RemoveEndpointResponseSchema);

router.put("/Endpoints/responseSchema/edit/:endpointId/:endpointResponseSchemaId/:username", EndpointController.EditEndpointResponseSchema);

router.put("/Endpoints/responseExample/add/:endpointId/:username", EndpointController.AddEndpointResponseExample);

router.put("/Endpoints/responseExample/remove/:endpointId/:endpointResponseExampleId/:username", EndpointController.RemoveEndpointResponseExample);

router.put("/Endpoints/responseExample/edit/:endpointId/:endpointResponseExampleId/:username", EndpointController.EditEndpointResponseExample);

router.put("/Endpoints/lastAccessedAt/:endpointId", EndpointController.SetLastAccessedAt);

router.put("/Endpoints/endpointDependency/add/:endpointId/:endpointResponseSchemaId/:username", EndpointController.AddEndpointDependency);

router.put("/Endpoints/endpointDependency/remove/:endpointId/:endpointDependencyId/:username", EndpointController.RemoveEndpointDependency);

router.put("/Endpoints/endpointDependency/edit/:endpointId/:endpointDependencyId/:username", EndpointController.EditEndpointDependency);

router.put("/Endpoints/endpointMetrics/:endpointId/:username", EndpointController.ModifyEndpointMetrics);

router.put("/Endpoints/versionHistory/:endpointId/:username", EndpointController.AddEndpointVersionHistory);

router.put("/Endpoints/versionHistory/:endpointId/:username", EndpointController.ModifyServerlessFunctionFile);

router.delete("/Endpoints/:endpointId", EndpointController.DeleteEndpoints);

module.exports = router;