const express = require('express');
const router = express.Router();
const ModelController = require('./ModelController');

router.get("/Model/:modelId", ModelController.GetModelById);

router.get("/Model/:modelName", ModelController.GetModelByName);

router.post("/Model/", ModelController.CreateModel);

router.put("/Model/:modelId/Version/:version", ModelController.EditVersion);

router.put("/Model/:modelId/ModelAttribute/add/:username", ModelController.AddModelAttribute);

router.put("/Model/:modelId/ModelAttribute/remove/:modelAttributeId/:username", ModelController.DeleteModelAttribute);

router.put("/Model/:modelId/ModelAttribute/edit/:modelAttributeId/:username", ModelController.EditModelAttribute);

router.put("/Model/:modelId/ModelDescription", ModelController.EditModelDescription);

router.put("/Model/:modelId/ModelCreationFile", ModelController.SetModelCreationFile);

router.put("/Model/:modelId/ModelDatabaseInfo", ModelController.SetModelDatabaseInfo);

router.put("/Model/:modelId/ModelChangelogInfo", ModelController.AddModelChangelog);

router.delete("/Model/:modelId", ModelController.DeleteModel);







module.exports = router;