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

exports.DeleteGroup = async (req, res) => {
    try {
        await GroupService.DeleteGroup(req.groupId);
        
        return res.status(201).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}


