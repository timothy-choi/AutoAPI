const ProjectStats = require('./ProjectStats');

exports.GetProjectStats = async (projectStatsId) => {
    var projectStats = await ProjectStats.findByPk(projectStatsId);

    return projectStats;
}

exports.GetProjectStatsByProjectId = async (projectId) => { 
    var projectStats = await ProjectStats.findOne({ where: {ProjectId: projectId }});

    return projectStats;
}                                                                                                            