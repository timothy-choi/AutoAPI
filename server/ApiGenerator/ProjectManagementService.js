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

exports.updateProjectStatsInfo = async (projectManagementId, projectStatsInfo) => {
    try {
        var projectManagement = await this.GetProjectManagementById(projectManagementId);

        if (!projectManagement) {
            throw new Error('Instance does not exist');
        }

        projectManagement.ProjectStatsInfo = projectStatsInfo;

        projectManagement.UpdatedAt = Date.now();

        await projectManagement.save();
    } catch (error) {
        throw new Error('Error updating project stats info:', error);
    }
}

exports.AddUser = async (projectManagementId, user) => {
    try {
        var projectManagement = await this.GetProjectManagementById(projectManagementId);

        if (!projectManagement) {
            throw new Error('Instance does not exist');
        }

        projectManagement.AllUsers.push(user);

        await projectManagement.save();
    } catch (error) {
        throw new Error('Error adding user:', error);
    }
}

exports.UpateUser = async (projectManagementId, user) => {
    try {
        var projectManagement = await this.GetProjectManagementById(projectManagementId);

        if (!projectManagement) {
            throw new Error('Instance does not exist');
        }

        var userIndex = projectManagement.AllUsers.findIndex(u => u.id === user.id);

        if (userIndex < 0) {
            throw new Error('User does not exist');
        }

        projectManagement.AllUsers[userIndex] = user;

        await projectManagement.save();
    } catch (error) {
        throw new Error('Error updating user:', error);
    }
}

exports.DeleteUser = async (projectManagementId, userId) => {
    try {
        var projectManagement = await this.GetProjectManagementById(projectManagementId);

        if (!projectManagement) {
            throw new Error('Instance does not exist');
        }

        projectManagement.AllUsers = projectManagement.AllUsers.filter(u => u.id != userId);

        await projectManagement.save();
    } catch (error) {
        throw new Error('Error deleting user:', error);
    }
}

exports.AddToApiMonitoringLog = async (projectManagementId, logEntry) => {
    try {
        var projectManagement = await this.GetProjectManagementById(projectManagementId);

        if (!projectManagement) {
            throw new Error('Instance does not exist');
        }

        projectManagement.ApiMonitoringLog.push(logEntry);

        await projectManagement.save();
    } catch (error) {
        throw new Error('Error adding to api monitoring log:', error);
    }
}

exports.AddToErrorLog = async (projectManagementId, logEntry) => {
    try {
        var projectManagement = await this.GetProjectManagementById(projectManagementId);

        if (!projectManagement) {
            throw new Error('Instance does not exist');
        }

        projectManagement.ErrorLog.push(logEntry);

        await projectManagement.save();
    } catch (error) {
        throw new Error('Error adding to error log:', error);
    }
}

exports.AddToSecurityIncidentLog = async (projectManagementId, logEntry) => {
    try {
        var projectManagement = await this.GetProjectManagementById(projectManagementId);

        if (!projectManagement) {
            throw new Error('Instance does not exist');
        }

        projectManagement.SecurityIncidentLog.push(logEntry);

        await projectManagement.save();
    } catch (error) {
        throw new Error('Error adding to security incident log:', error);
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


