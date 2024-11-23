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
