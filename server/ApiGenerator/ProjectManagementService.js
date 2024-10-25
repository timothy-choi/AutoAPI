const ProjectManagement = require('./ProjectManagement');

exports.GetProjectManagementById = async (projectManagementId) => {
    var projectManagement = await ProjectManagement.findByPk(projectManagementId);

    return projectManagement;
}

exports.GetProjectManagementByProjectId = async (projectId) => {
    var projectManagement = await ProjectManagement.findOne({where: {ProjectId: projectId}});

    return projectManagement;
}

