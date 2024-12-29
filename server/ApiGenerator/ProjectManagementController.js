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

exports.deleteProjectManagement = async (req, res) => {
    try {
        await ProjectManagementHelper.deleteProjectManagement(req.projectManagementId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};