const ProjectService = require('./ProjectService');


exports.GetProjectById = async (req, res) => {
    try {
        var project = await ProjectService.GetProjectById(req.projectId);

        return res.status(200).body(project);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.GetProjectByName = async (req, res) => {
    try {
        var project = await ProjectService.GetProjectByName(req.projectName);

        return res.status(200).body(project);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddModelToProject = async (req, res) => {
    try {
        await ProjectService.AddModelToProject(req.projectId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveModelToProject = async (req, res) => {
    try {
        await ProjectService.RemoveModelToProject(req.projectId, req.projectModelId);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditModelToProject = async (req, res) => {
    try {
        await ProjectService.EditModelToProject(req.projectId, req.modelInfoId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddDatabaseToProject = async (req, res) => {
    try {
        await ProjectService.AddDatabaseToProject(req.projectId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveDatabaseFromProject = async (req, res) => {
    try {
        await ProjectService.RemoveDatabaseFromProject(req.projectId, req.databaseInfoId);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditDatabaseInfo = async (req, res) => {
    try {
        await ProjectService.EditDatabaseInfo(req.projectId, req.databaseInfoId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditProjectEndpoints = async (req, res) => {
    try {
        await ProjectService.ModifyProjectEndpoints(req.projectId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddServerlessFunction = async (req, res) => {
    try {
        await ProjectService.AddServerlessFunction(req.projectId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveServerlessFunction = async (req, res) => {
    try {
        await ProjectService.RemoveServerlessFunction(req.projectId, req.serverlessFunctionInfoId);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditServerlessFunctionInfo = async (req, res) => {
    try {
        await ProjectService.EditServerlessFunctionInfo(req.projectId, req.serverlessFunctionInfoId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}