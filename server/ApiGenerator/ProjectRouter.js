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


module.exports = router;