const ProjectManagement = require('./ProjectManagement');

exports.GetProjectManagementById = async (projectManagementId) => {
    var projectManagement = await ProjectManagement.findByPk(projectManagementId);

    return projectManagement;
}