const UserService = require('./UserService');


exports.GetUserById = async (req, res) => {
    try {
        var user = await UserService.GetUserById(req.userId);

        return res.status(200).body(user);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.GetUserByUsername = async (req, res) => {
    try {
        var user = await UserService.GetUserByUsername(req.username);

        return res.status(200).body(user);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.CreateUser = async (req, res) => {
    try {
        var user = await UserService.GetUserByUsername(req.body.username);

        if (user) {
            return res.status(500).json({ error: 'User already exists'});
        }

        var userInfo = await UserService.CreateUser(req.body);

        return res.status(201).json(userInfo);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.ReplaceUsername = async (req, res) => {
    try {
        await UserService.ReplaceUsername(req.userId, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.ReplaceEmail = async (req, res) => {
    try {
        await UserService.ReplaceEmail(req.userId, req.email);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetJoinedGroup = async (req, res) => {
    try {
        await UserService.SetJoinedGroup(req.userId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddPastGroupId = async (req, res) => {
    try {
        await UserService.AddPastGroupId(req.userId, req.groupId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddCurrentGroupId = async (req, res) => {
    try {
        await UserService.AddCurrentGroupId(req.userId, req.groupId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveCurrentGroupId = async (req, res) => {
    try {
        await UserService.RemoveCurrentGroupId(req.userId, req.groupId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddAllCollaborators = async (req, res) => {
    try {
        await UserService.AddAllCollaborators(req.userId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveAllCollaborators = async (req, res) => {
    try {
        await UserService.RemoveAllCollaborators(req.userId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetNotificationsOn = async (req, res) => {
    try {
        await UserService.SetNotificationsOn(req.userId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
} 

exports.SetNotificationType = async (req, res) => {
    try {
        await UserService.SetNotificationType(req.userId, req.notificationType);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
} 

exports.SetIsAvailable = async (req, res) => {
    try {
        await UserService.SetIsAvailable(req.userId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
} 

exports.AddApiProjectsCreated = async (req, res) => {
    try {
        await UserService.AddApiProjectsCreated(req.userId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveApiProjectsCreated = async (req, res) => {
    try {
        await UserService.RemoveApiProjectsCreated(req.userId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddApiProjectsContributed = async (req, res) => {
    try {
        await UserService.AddApiProjectsContributed(req.userId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveApiProjectsContributed = async (req, res) => {
    try {
        await UserService.RemoveApiProjectsContributed(req.userId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddCurrentApiProjects = async (req, res) => {
    try {
        await UserService.AddCurrentApiProjects(req.userId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveCurrentApiProjects = async (req, res) => {
    try {
        await UserService.RemoveCurrentApiProjects(req.userId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddApiProjectsWithAccess = async (req, res) => {
    try {
        await UserService.AddApiProjectsWithAccess(req.userId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveApiProjectsWithAccess = async (req, res) => {
    try {
        await UserService.RemoveApiProjectsWithAccess(req.userId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddApiProjectsViewHistory = async (req, res) => {
    try {
        await UserService.AddApiProjectsViewHistory(req.userId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveApiProjectsViewHistory = async (req, res) => {
    try {
        await UserService.RemoveApiProjectsViewHistory(req.userId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddCloudProviderInfo = async (req, res) => {
    try {
        await UserService.AddCloudProviderInfo(req.userId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveCloudProviderInfo = async (req, res) => {
    try {
        await UserService.RemoveCloudProviderInfo(req.userId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddActivityLog = async (req, res) => {
    try {
        await UserService.AddActivityLog(req.userId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetCloudProviderDefault = async (req, res) => {
    try {
        await UserService.SetCloudProviderDefault(req.userId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.DeleteUser = async (req, res) => {
    try {
        var user = await UserService.GetUserById(req.userId);

        if (user) {
            return res.status(500).json({ error: 'User already exists'});
        }

        await UserService.DeleteUser(req.userId);

        return res.status(201).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}
