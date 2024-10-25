const Project = require('./Project');

exports.GetProjectById = async (projectId) => {
    var project = await Project.findByPk(projectId);

    return project;
}

exports.GetProjectByName = async (projectName) => {
    var project = await Project.findOne({where: {ProjectApiName: projectName}});

    return project;
}