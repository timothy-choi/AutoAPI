const ProjectService = require('./ProjectService');


exports.GetProjectById = async (req, res) => {
    try {
        var project = await ProjectService.GetProjectById(req.projectId);

        return res.status(200).body(project);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.GetProjectByName = async (req, res) => {
    try {
        var project = await ProjectService.GetProjectByName(req.projectName);

        return res.status(200).body(project);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddModelToProject = async (req, res) => {
    try {
        await ProjectService.AddModelToProject(req.projectId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveModelToProject = async (req, res) => {
    try {
        await ProjectService.RemoveModelToProject(req.projectId, req.projectModelId);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditModelToProject = async (req, res) => {
    try {
        await ProjectService.EditModelToProject(req.projectId, req.modelInfoId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddDatabaseToProject = async (req, res) => {
    try {
        await ProjectService.AddDatabaseToProject(req.projectId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveDatabaseFromProject = async (req, res) => {
    try {
        await ProjectService.RemoveDatabaseFromProject(req.projectId, req.databaseInfoId);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditDatabaseInfo = async (req, res) => {
    try {
        await ProjectService.EditDatabaseInfo(req.projectId, req.databaseInfoId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditProjectEndpoints = async (req, res) => {
    try {
        await ProjectService.ModifyProjectEndpoints(req.projectId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddServerlessFunction = async (req, res) => {
    try {
        await ProjectService.AddServerlessFunction(req.projectId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveServerlessFunction = async (req, res) => {
    try {
        await ProjectService.RemoveServerlessFunction(req.projectId, req.serverlessFunctionInfoId);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditServerlessFunctionInfo = async (req, res) => {
    try {
        await ProjectService.EditServerlessFunctionInfo(req.projectId, req.serverlessFunctionInfoId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditCloudProvider = async (req, res) => {
    try {
        await ProjectService.EditCloudProviderInfo(req.projectId, req.cloudProvider);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditIsPrivate = async (req, res) => {
    try {
        await ProjectService.EditIsPrivate(req.projectId, req.isPrivate);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditProjectStatus = async (req, res) => {
    try {
        await ProjectService.EditProjectStatus(req.projectId, req.projectStatus);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditProjectHealthStatus = async (req, res) => {
    try {
        await ProjectService.EditProjectHealthStatus(req.projectId, req.projectHealthStatus);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetIsAvailable = async (req, res) => {
    try {
        await ProjectService.SetIsAvailable(req.projectId);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetGithubUrl = async (req, res) => {
    try {
        await ProjectService.SetGithubUrl(req.projectId, req.body.githubUrl);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddProjectUpdate = async (req, res) => {
    try {
        await ProjectService.AddProjectUpdate(req.projectId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditProjectUpdate = async (req, res) => {
    try {
        await ProjectService.EditProjectUpdate(req.projectId, req.projectUpdateId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetProjectBucket = async (req, res) => {
    try {
        await ProjectService.SetProjectBucket(req.projectId, req.projectFileBucket);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddProjectActivityLog = async (req, res) => {
    try {
        await ProjectService.AddProjectActivityLog(req.projectId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddProjectRequestEntry = async (req, res) => {
    try {
        await ProjectService.AddProjectUserRequestsHistoryEntry(req.projectId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveProjectRequestEntry = async (req, res) => {
    try {
        await ProjectService.RemoveProjectUserRequestsHistoryEntry(req.projectId, req.projectRequestId);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditProjectRequestEntry = async (req, res) => {
    try {
        await ProjectService.EditProjectUserRequestsHistoryEntry(req.projectId, req.projectRequestId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddAssignedUserProjectUserRequests = async (req, res) => {
    try {
        await ProjectService.AddAssignedUserProjectUserRequests(req.projectId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveAssignedUserProjectUserRequests = async (req, res) => {
    try {
        await ProjectService.RemoveAssignedUserProjectUserRequests(req.projectId, req.projectUserRequestId);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditAssignedUserProjectUserRequests = async (req, res) => {
    try {
        await ProjectService.EditAssignedUserProjectUserRequests(req.projectId, req.projectUserRequestId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddAwaitingUserProjectUserRequests = async (req, res) => {
    try {
        await ProjectService.AddAwaitingUserProjectUserRequests(req.projectId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveAwaitingUserProjectUserRequests = async (req, res) => {
    try {
        await ProjectService.RemoveAwaitingUserProjectUserRequests(req.projectId, req.projectUserRequestId);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditAwaitingUserProjectUserRequests = async (req, res) => {
    try {
        await ProjectService.EditAwaitingUserProjectUserRequests(req.projectId, req.projectUserRequestId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddProjectContributor = async (req, res) => {
    try {
        await ProjectService.AddProjectContributor(req.projectId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveProjectContributor = async (req, res) => {
    try {
        await ProjectService.RemoveProjectContributor(req.projectId, req.projectContributorId);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditAwaitingUserProjectUserRequests = async (req, res) => {
    try {
        await ProjectService.EditProjectContributor(req.projectId, req.projectContributorId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetProjectCloudInfo = async (req, res) => {
    try {
        await ProjectService.SetProjectCloudInfo(req.projectId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddProjectViewRequestsRecieved = async (req, res) => {
    try {
        await ProjectService.AddProjectViewRequestsRecieved(req.projectId, req.body);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveProjectViewRequestsRecieved = async (req, res) => {
    try {
        await ProjectService.RemoveProjectViewRequestsRecieved(req.projectId, req.viewRequestId);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}