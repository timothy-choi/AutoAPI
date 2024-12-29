const ProjectManagementHelper = require('./ProjectManagementService');

exports.GetProjectManagementById = async (req, res) => {
    try {
        var projectManagement = await ProjectManagementHelper.GetProjectManagementById(req.projectManagementId);

        return res.status(200).send(projectManagement);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.GetProjectManagementByProjectId = async (req, res) => {
    try {
        var projectManagement = await ProjectManagementHelper.GetProjectManagementByProjectId(req.projectId);

        return res.status(200).send(projectManagement);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.CreateProjectManagement = async (req, res) => {
    try {
        var projectManagement = await ProjectManagementHelper.createProjectManagement(req.body);

        return res.status(201).send(projectManagement);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.setGroupId = async (req, res) => {
    try {
        await ProjectManagementHelper.setGroupId(req.projectManagementId, req.groupId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.setUserId = async (req, res) => {
    try {
        await ProjectManagementHelper.setUserId(req.projectManagementId, req.userId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.updateProjectStatsInfo = async (req, res) => {
    try {
        await ProjectManagementHelper.updateProjectStatsInfo(req.projectManagementId, req.body.projectStatsInfo);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.addUserToProject = async (req, res) => {
    try {
        await ProjectManagementHelper.AddUser(req.projectManagementId, req.body);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.updateUserInProject = async (req, res) => {
    try {
        await ProjectManagementHelper.UpateUser(req.projectManagementId, req.body);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteUserFromProject = async (req, res) => {
    try {
        await ProjectManagementHelper.DeleteUser(req.projectManagementId, req.userId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteProjectManagement = async (req, res) => {
    try {
        await ProjectManagementHelper.deleteProjectManagement(req.projectManagementId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};