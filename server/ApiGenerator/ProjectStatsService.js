const ProjectStats = require('./ProjectStats');

exports.GetProjectStats = async (projectStatsId) => {
    var projectStats = await ProjectStats.findByPk(projectStatsId);

    return projectStats;
}

exports.GetProjectStatsByProjectId = async (projectId) => { 
    var projectStats = await ProjectStats.findOne({ where: {ProjectId: projectId }});

    return projectStats;
}             

exports.CreateProjectStats = async (projectStatsInfo) => {
    try {
        var currProjectStats = await ProjectStats.findOne({ where: {ProjectId: projectStatsInfo['projectId'] }});

        if (currProjectStats) {
            throw new Error('group already exists');
        }

        const projectStats = await ProjectStats.create({ProjectId: projectStatsInfo['projectId'], CreatedAt: Date.now()});

        return projectStats;
    } catch (error) {
        throw new Error('Could not create group');
    }
}

exports.SetNumberOfViews = async (projectStatsId) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.NumberOfViews += 1;

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.SetNumberOfApiCalls = async (projectStatsId) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.NumberOfApiCallsSent += 1;

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.AddApiEndpointUsage = async (projectStatsId, apiEndpointUsageInfo) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.ApiEndpointUsage.add(apiEndpointUsageInfo);

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.RemoveApiEndpointUsage = async (projectStatsId, apiEndpointUsageId) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.ApiEndpointUsage.filter(endpoint = endpoint.id != apiEndpointUsageId);

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.EditApiEndpointUsage = async (projectStatsId, apiEndpointUsageId, updatedInfo) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        var index = projectStats.ApiEndpointUsage.findIndex(endpoint = endpoint.id != apiEndpointUsageId);

        projectStats.ApiEndpointUsage.splice(index, 1);

        projectStats.ApiEndpointUsage.splice(index, 0, updatedInfo);

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.AddContributorStats = async (projectStatsId, contributorStats) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.ContributorStats.add(contributorStats);

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.EditContributorStats = async (projectStatsId, contributorStatsId, updatedInfo) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        var index = projectStats.ContributorStats.findIndex(contributor = contributor.id != contributorStatsId);

        projectStats.ContributorStats.splice(index, 1);

        projectStats.ContributorStats.splice(index, 0, updatedInfo);

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.SetAverageResponseTime = async (projectStatsId, avgResponseTime) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.AverageResponseTime = avgResponseTime;

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.SetUptimePercentage = async (projectStatsId, uptimePercentage) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.UptimePercentage = uptimePercentage;

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.SetErrorRate = async (projectStatsId, errorRate) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.ErrorRate = errorRate;

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.DeleteProjectStats = async (projectStatsId) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        await ProjectStats.destroy();
    } catch (error) {
        throw new Error('could not delete project stats');
    }
}