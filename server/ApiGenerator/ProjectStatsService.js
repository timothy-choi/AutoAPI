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

        const projectStats = await ProjectStats.create({ProjectId: projectStatsInfo['projectId']});

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