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

exports.setNumberOfViews = async (req, res) => {
    try {
        await projectStatsService.SetNumberOfViews(req.projectStatsId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.setNumberOfApiCalls = async (req, res) => {
    try {
        await projectStatsService.SetNumberOfApiCalls(req.projectStatsId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.addApiEndpointUsage = async (req, res) => {
    try {
        await projectStatsService.AddApiEndpointUsage(req.projectStatsId, req.body);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.removeApiEndpointUsage = async (req, res) => {
    try {
        await projectStatsService.RemoveApiEndpointUsage(req.projectStatsId, req.apiEndpointUsageId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.updateApiEndpointUsage = async (req, res) => {
    try {
        await projectStatsService.EditApiEndpointUsage(req.projectStatsId, req.apiEndpointUsageId, req.body);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.addContributorStats = async (req, res) => {
    try {
        await projectStatsService.AddContributorStats(req.projectStatsId, req.body);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.editContributorStats = async (req, res) => {
    try {
        await projectStatsService.EditContributorStats(req.projectStatsId, req.contributorStatsId, req.body);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.setAverageResponseTime = async (req, res) => {
    try {
        await projectStatsService.SetAverageResponseTime(req.projectStatsId, req.avgResponseTime);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.setUptimePercentage = async (req, res) => {
    try {
        await projectStatsService.SetUptimePercentage(req.projectStatsId, req.uptimePercentage);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.setErrorRate = async (req, res) => {
    try {
        await projectStatsService.SetErrorRate(req.projectStatsId, req.errorRate);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.addTopErrorCodes = async (req, res) => {
    try {
        await projectStatsService.SetTopErrorCodes(req.projectStatsId, req.body);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.editTopErrorCode = async (req, res) => {
    try {
        await projectStatsService.EditTopErrorCode(req.projectStatsId, req.topErrorCodeId, req.body);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.addGeoDistribution = async (req, res) => {
    try {
        await projectStatsService.AddGeoDistribution(req.projectStatsId, req.body);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.editGeoDistribution = async (req, res) => {
    try {
        await projectStatsService.SetGeoDistribution(req.projectStatsId, req.geoDistributionId, req.body);

        return res.status(200).send(null);
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