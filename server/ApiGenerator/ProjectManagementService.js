const ProjectManagement = require('./ProjectManagement');

exports.GetProjectManagementById = async (projectManagementId) => {
    var projectManagement = await ProjectManagement.findByPk(projectManagementId);

    return projectManagement;
}

exports.GetProjectManagementByProjectId = async (projectId) => {
    var projectManagement = await ProjectManagement.findOne({where: {ProjectId: projectId}});

    return projectManagement;
}

exports.createProjectManagement = async (projectManagementData) => {
    try {
        var projectManagement = new ProjectManagement(projectManagementData.projectManagementId);

        if (projectManagement) {
            throw new Error('Instance already exists');
        }

        projectManagement = await ProjectManagement.create({
            ProjectId: projectManagementData.projectId,
            CreatedAt: projectManagementData.createdAt
        });

        return projectManagement;
    } catch (error) {
        throw new Error('Error creating project management:', error);
    }
}

exports.setGroupId = async (projectManagementId, groupId) => {
    try {
        var projectManagement = await this.GetProjectManagementById(projectManagementId);

        if (!projectManagement) {
            throw new Error('Instance does not exist');
        }

        projectManagement.GroupId = groupId;

        await projectManagement.save();
    } catch (error) {
        throw new Error('Error setting group id:', error);
    }
}

exports.setUserId = async (projectManagementId, userId) => {
    try {
        var projectManagement = await this.GetProjectManagementById(projectManagementId);

        if (!projectManagement) {
            throw new Error('Instance does not exist');
        }

        projectManagement.UserId = userId;

        await projectManagement.save();
    } catch (error) {
        throw new Error('Error setting user id:', error);
    }
}

exports.deleteProjectManagement = async (projectManagementId) => {
    try {
        var projectManagement = await this.GetProjectManagementById(projectManagementId);

        if (!projectManagement) {
            throw new Error('Instance does not exist');
        }

        await projectManagement.destroy();
    } catch (error) {
        throw new Error('Error deleting project management:', error);
    }
}


