const Project = require('./Project');

exports.GetProjectById = async (projectId) => {
    var project = await Project.findByPk(projectId);

    return project;
}

exports.GetProjectByName = async (projectName) => {
    var project = await Project.findOne({where: {ProjectApiName: projectName}});

    return project;
}

exports.AddModelToProject = async (projectId, modelInfo) => {
    try {
        var project = await Project.findByPk(projectId);

        if (!project) {
            throw new Exception('Can not get project');
        }

        project.AllProjectModels.push(modelInfo);

        project.ModifiedAt = Date.now;

        await project.save();
    } catch (error) {
        throw new Exception('Can not edit project');
    }
}

exports.RemoveModelToProject = async (projectId, modelInfoId) => {
    try {
        var project = await GetProjectById(projectId);

        if (!project) {
            throw new Error('Project does not exist');
        } 

        project.AllProjectModels.filter(modelInfo => modelInfo.id != modelInfoId);

        project.ModifiedAt = Date.now();

        await project.save();
    } catch (error) {
        throw new Exception('Can not edit project');
    }
}

exports.EditModelToProject = async (projectId, modelInfoId, updatedModelInfo) => {
    try {
        var project = await GetProjectById(projectId);

        if (!project) {
            throw new Error('Model does not exist');
        } 

        var index = project.AllProjectModels.findIndex(modelInfo = modelInfo.id != modelInfoId);

        project.AllProjectModels.splice(index, 1);

        project.AllProjectModels.splice(index, 0, updatedModelInfo);

        project.ModifiedAt = Date.now();

        await project.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.AddDatabaseToProject = async (projectId, databaseInfo) => {
    try {
        var project = await Project.findByPk(projectId);

        if (!project) {
            throw new Exception('Can not get project');
        }

        project.AllDatabases.push(databaseInfo);

        project.ModifiedAt = Date.now;

        await project.save();
    } catch (error) {
        throw new Exception('Can not edit project');
    }
}

exports.RemoveDatabaseFromProject = async (projectId, databaseInfoId) => {
    try {
        var project = await GetProjectById(projectId);

        if (!project) {
            throw new Error('Project does not exist');
        } 

        project.AllDatabases.filter(databaseInfo => databaseInfo.id != databaseInfoId);

        project.ModifiedAt = Date.now();

        await project.save();
    } catch (error) {
        throw new Exception('Can not edit project');
    }
}

exports.EditDatabaseInfo = async (projectId, databaseInfoId, updatedDatabaseInfo) => {
    try {
        var project = await GetProjectById(projectId);

        if (!project) {
            throw new Error('Model does not exist');
        } 

        var index = project.AllDatabases.findIndex(databaseInfo = databaseInfo.id != databaseInfoId);

        project.AllDatabases.splice(index, 1);

        project.AllDatabases.splice(index, 0, updatedDatabaseInfo);

        project.ModifiedAt = Date.now();

        await project.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.ModifyProjectEndpoints = async (projectId, endpointsInfo) => {
    try {
        var project = await GetProjectById(projectId);

        if (!project) {
            throw new Error('Model does not exist');
        } 

        project.AllEndpoints = endpointsInfo;

        project.ModifiedAt = Date.now();

        await project.save();
    } catch (error) {
        throw new Exception('Can not edit project');
    }
}

exports.AddServerlessFunction = async (projectId, serverlessFunctionInfo) => {
    try {
        var project = await Project.findByPk(projectId);

        if (!project) {
            throw new Exception('Can not get project');
        }

        project.ApiServerlessFunctions.push(serverlessFunctionInfo);

        project.ModifiedAt = Date.now;

        await project.save();
    } catch (error) {
        throw new Exception('Can not edit project');
    }
}

exports.RemoveServerlessFunction = async (projectId, serverlessFunctionInfoId) => {
    try {
        var project = await GetProjectById(projectId);

        if (!project) {
            throw new Error('Project does not exist');
        } 

        project.ApiServerlessFunctions.filter(serverlessFunctionInfo => serverlessFunctionInfo.id != serverlessFunctionInfoId);

        project.ModifiedAt = Date.now();

        await project.save();
    } catch (error) {
        throw new Exception('Can not edit project');
    }
}

exports.EditServerlessFunctionInfo = async (projectId, serverlessFunctionInfoId, updatedServerlessFunctionInfo) => {
    try {
        var project = await GetProjectById(projectId);

        if (!project) {
            throw new Error('Model does not exist');
        } 

        var index = project.ApiServerlessFunctions.findIndex(serverlessFunctionInfo = serverlessFunctionInfo.id != serverlessFunctionInfoId);

        project.ApiServerlessFunctions.splice(index, 1);

        project.ApiServerlessFunctions.splice(index, 0, updatedServerlessFunctionInfo);

        project.ModifiedAt = Date.now();

        await project.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.EditCloudProviderInfo = async (projectId, cloudProvider) => {
    try {
        var project = await Project.findByPk(projectId);

        if (!project) {
            throw new Exception('Can not get project');
        }

        project.ProjectApiCloudProvider = cloudProvider;

        project.ModifiedAt = Date.now;

        await project.save();
    } catch (error) {
        throw new Exception('Can not edit project');
    }
}

exports.EditIsPrivate = async (projectId, isPrivate) => {
    try {
        var project = await Project.findByPk(projectId);

        if (!project) {
            throw new Exception('Can not get project');
        }

        project.IsPrivate = isPrivate;

        project.ModifiedAt = Date.now;

        await project.save();
    } catch (error) {
        throw new Exception('Can not edit project');
    }
}

exports.EditProjectStatus = async (projectId, projectStatus) => {
    try {
        var project = await Project.findByPk(projectId);

        if (!project) {
            throw new Exception('Can not get project');
        }

        project.ProjectStatus = projectStatus;

        project.ModifiedAt = Date.now;

        await project.save();
    } catch (error) {
        throw new Exception('Can not edit project');
    }
}

exports.EditProjectHealthStatus = async (projectId, projectHealthStatus) => {
    try {
        var project = await Project.findByPk(projectId);

        if (!project) {
            throw new Exception('Can not get project');
        }

        project.ProjectHealthStatus = projectHealthStatus;

        project.ModifiedAt = Date.now;

        await project.save();
    } catch (error) {
        throw new Exception('Can not edit project');
    }
}

exports.SetIsAvailable = async (projectId) => {
    try {
        var project = await Project.findByPk(projectId);

        if (!project) {
            throw new Exception('Can not get project');
        }

        project.IsAvailable = project.IsAvailable ? true : false;

        project.ModifiedAt = Date.now;

        await project.save();
    } catch (error) {
        throw new Exception('Can not edit project');
    }
}

