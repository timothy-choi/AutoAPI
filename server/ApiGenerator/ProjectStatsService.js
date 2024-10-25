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

        var index = projectStats.ContributorStats.findIndex(contributor = contributor.id == contributorStatsId);

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

exports.SetTopErrorCodes = async (projectStatsId, topErrorCodes) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.TopErrorCodes = topErrorCodes;

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.EditTopErrorCode = async (projectStatsId, topErrorCodeId, updatedErrorCodeInfo) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        var index = projectStats.TopErrorCodes.find(errorCode = errorCode.id == topErrorCodeId);

        projectStats.TopErrorCodes.splice(index, 1);

        projectStats.TopErrorCodes.splice(index, 0, updatedErrorCodeInfo);

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.AddGeoDistribution = async (projectStatsId, geoDistInfo) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.GeoDistribution.add(geoDistInfo);

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.SetGeoDistribution = async (projectStatsId ,geoDistId, updatedGeoDistInfo) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        var index = projectStats.GeoDistribution.find(geoDist = geoDist.id == geoDistId);

        projectStats.GeoDistribution.splice(index, 1);

        projectStats.GeoDistribution.splice(index, 0, updatedGeoDistInfo);

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.SetPeakTrafficTime = async (projectStatsId, peakTrafficTime) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.PeakTrafficTime = peakTrafficTime;

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.SetMaxResponseTime = async (projectStatsId, maxResponseTime) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.MaxResponseTime = maxResponseTime;

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.SetMinResponseTime = async (projectStatsId, minResponseTime) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.MinResponseTime = minResponseTime;

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.SetTotalDataTransferred = async (projectStatsId, totalDataTransferred) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.TotalDataTransferred = totalDataTransferred;

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.AddRequestTypeDistribution = async (projectStatsId, requestTypeDistInfo) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.RequestTypeDistribution.add(requestTypeDistribution);

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.RemoveRequestTypeDistribution = async (projectStatsId, requestTypeDistId) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.RequestTypeDistribution.filter(requestDist = requestDist.id != requestTypeDistId);

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.EditRequestTypeDistribution = async (projectStatsId, requestTypeDistId, updatedRequestDistInfo) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        var index = projectStats.RequestTypeDistribution.find(requestDistribution = requestDistribution.id == requestTypeDistId);

        projectStats.RequestTypeDistribution.splice(index, 1);

        projectStats.RequestTypeDistribution.splice(index, 0, updatedRequestDistInfo);

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.AddActiveUser = async (projectStatsId, activeUser) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.ActiveUsers.add(activeUser);

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.RemoveActiveUser = async (projectStatsId, activeUserId) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.ActiveUsers.filter(activeUser = activeUser.id != activeUserId);

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.EditActiveUser = async (projectStatsId, activeUserId, updatedActiveUser) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        var index = projectStats.ActiveUsers.find(activeUsers = activeUsers.id == activeUserId);

        projectStats.ActiveUsers.splice(index, 1);

        projectStats.ActiveUsers.splice(index, 0, updatedActiveUser);

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.AddLatencyByRegion = async (projectStatsId, latencyInfo) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.LatencyByRegion.add(latencyInfo);

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.RemoveLatencyByRegion = async (projectStatsId, latencyInfoId) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        projectStats.LatencyByRegion.filter(latencyInfo = latencyInfo.id != latencyInfoId);

        projectStats.UpdatedAt = Date.now();

        await projectStats.save();
    } catch (error) {
        throw new Error('could not set project stats');
    }
}

exports.EditLatencyByRegion = async (projectStatsId, latencyId, updatedLatency) => {
    try {
        var projectStats = await ProjectStats.findByPk(projectStatsId);

        if (!projectStats) {
            throw new Error('project stats does not exist');
        }

        var index = projectStats.LatencyByRegion.find(latencyInfo = latencyInfo.id == latencyId);

        projectStats.LatencyByRegion.splice(index, 1);

        projectStats.LatencyByRegion.splice(index, 0, updatedLatency);

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