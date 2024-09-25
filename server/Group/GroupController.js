const GroupService = require('./GroupService');

exports.GetGroupById = async (req, res) => {
    try {
        var group = await GroupService.GetGroupById(req.groupId);
        
        return res.status(200).json(group);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.GetGroupByGroupName = async (req, res) => {
    try {
        var group = await GroupService.GetGroupByGroupName(req.groupName);
        
        return res.status(200).json(group);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.CreateGroup = async (req, res) => {
    try {
        var group = await GroupService.CreateGroup(req.body);
        
        return res.status(201).json(group);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.AddGroupUsers = async (req, res) => {
    try {
        await GroupService.AddGroupUsers(req.groupId, req.body);
        
        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.RemoveGroupUsers = async (req, res) => {
    try {
        await GroupService.RemoveGroupUsers(req.groupId, req.body);
        
        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.SetCanJoin = async (req, res) => {
    try {
        await GroupService.SetCanJoin(req.groupId);
        
        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.SetProject = async (req, res) => {
    try {
        await GroupService.SetProject(req.groupId, req.body);
        
        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.SetPrivateMode = async (req, res) => {
    try {
        await GroupService.SetPrivateMode(req.groupId);
        
        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.AddUserJoinRequests = async (req, res) => {
    try {
        await GroupService.AddUserJoinRequests(req.groupId, req.body);
        
        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.RemoveUserJoinRequests = async (req, res) => {
    try {
        await GroupService.RemoveUserJoinRequests(req.groupId, req.body);
        
        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.AddUserViewRequests = async (req, res) => {
    try {
        await GroupService.AddUserViewRequests(req.groupId, req.body);
        
        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.RemoveUserViewRequests = async (req, res) => {
    try {
        await GroupService.RemoveUserViewRequests(req.groupId, req.body);
        
        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.AddGroupActivityLog = async (req, res) => {
    try {
        await GroupService.AddGroupActivityLog(req.groupId, req.body);
        
        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.AddGroupTag = async (req, res) => {
    try {
        await GroupService.AddGroupTag(req.groupId, req.groupTag);
        
        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.RemoveGroupTag = async (req, res) => {
    try {
        await GroupService.RemoveGroupTag(req.groupId, req.groupTag);
        
        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.SetGroupDescription = async (req, res) => {
    try {
        await GroupService.SetGroupDescription(req.groupId, req.body.groupDescription);
        
        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.DeleteGroup = async (req, res) => {
    try {
        await GroupService.DeleteGroup(req.groupId);
        
        return res.status(201).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}


