const projectStatsService = require('./ProjectStatsService');

exports.GetProjectStatsById = async (req, res) => {
    try {
        var projectStats = await projectStatsService.GetProjectStats(req.projectStatsId);

        return res.status(200).send(projectStats);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.GetProjectStatsByProjectId = async (req, res) => {
    try {
        var projectStats = await projectStatsService.GetProjectStatsByProjectId(req.projectId);

        return res.status(200).send(projectStats);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.CreateProjectStats = async (req, res) => {
    try {
        var projectStats = await projectStatsService.CreateProjectStats(req.body);

        return res.status(201).send(projectStats);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteProjectStats = async (req, res) => {
    try {
        await projectStatsService.DeleteProjectStats(req.projectStatsId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};