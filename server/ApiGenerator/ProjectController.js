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
