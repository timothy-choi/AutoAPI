const express = require('express');
const router = express.Router();
const UserController = require('./UserController');

router.get("/:userId", UserController.GetUserById);

router.get("/username/:username", UserController.GetUserByUsername);

router.post("/", UserController.CreateUser);

router.delete("/:userId", UserController.DeleteUser);

router.put("/username/:userId/:username", UserController.ReplaceUsername);

router.put("/email/:userId/:email", UserController.ReplaceEmail);

router.put("/groupJoined/:userId", UserController.SetJoinedGroup);

router.put("/pastGroupId/add/:userId/:groupId", UserController.AddPastGroupId);

router.put("/currentGroupId/add/:userId/:groupId", UserController.AddCurrentGroupId);

router.put("/currentGroupId/remove/:userId/:groupId", UserController.RemoveCurrentGroupId);

router.put("/allCollaborators/add/:userId", UserController.AddAllCollaborators);

router.put("/allCollaborators/remove/:userId", UserController.RemoveAllCollaborators);

router.put("/notificationsOn/:userId", UserController.SetNotificationsOn);

router.put("/notificationType/:userId/:notificationType", UserController.SetNotificationType);

router.put("/isAvailable/:userId", UserController.SetIsAvailable);

router.put("/apiProjectsCreated/add/:userId", UserController.AddApiProjectsCreated);

router.put("/apiProjectsCreated/remove/:userId", UserController.RemoveApiProjectsCreated);

router.put("/apiProjectsContributed/add/:userId", UserController.AddApiProjectsContributed);

router.put("/apiProjectsContributed/remove/:userId", UserController.RemoveApiProjectsContributed);

router.put("/currentApiProjects/add/:userId", UserController.AddCurrentApiProjects);

router.put("/currentApiProjects/remove/:userId", UserController.RemoveCurrentApiProjects);

router.put("/apiProjectsWithAccess/add/:userId", UserController.AddApiProjectsWithAccess);

router.put("/apiProjectsWithAccess/remove/:userId", UserController.RemoveApiProjectsWithAccess);

router.put("/apiProjectsViewHistory/add/:userId", UserController.AddApiProjectsViewHistory);

router.put("/apiProjectsViewHistory/remove/:userId", UserController.RemoveApiProjectsViewHistory);

router.put("/cloudProviderInfo/add/:userId", UserController.AddCloudProviderInfo);

router.put("/cloudProviderInfo/remove/:userId", UserController.RemoveCloudProviderInfo);

router.put("/activityLog/:userId", UserController.AddActivityLog);

router.put("/cloudProviderDefault/:userId", UserController.SetCloudProviderDefault);

module.exports = router;