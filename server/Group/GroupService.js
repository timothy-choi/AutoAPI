const { Group } = require('../Group/Group'); 

exports.GetGroupById = async (groupId) => {
    var groupInfo = await Group.findByPk(groupId);

    return groupInfo;
}

exports.GetGroupByGroupName = async (groupName) => {
    var groupInfo = await Group.findOne({ where: { GroupName: groupName } });

    return groupInfo;
}

exports.CreateGroup = async (groupBody) => {
    try {
        var groupInfo = await Group.Group.findOne({ where: { GroupName: groupBody['groupName'] } });

        if (groupInfo) {
            throw new Error('group already exists');
        }

        const group = await Group.create({GroupName: groupBody['groupName'], GroupUsers: groupBody['groupUsers'], CanJoin: groupBody['canJoin'], PrivateMode: groupBody['privateMode'], CreatedAt: Date.now(), GroupDescription: groupBody['groupDescription']});

        return group;
    } catch (error) {
        throw new Error('Could not create group');
    }
}

exports.SetProject = async (groupId, project) => {
    try {
        var groupInfo = await Group.findByPk(groupId);

        if (!groupInfo) {
            throw new Error('group does not exist');
        } 

        groupInfo.GroupProject = project;

        groupInfo.LastUpdatedAt = Date.now();

        await groupInfo.save();
    } catch (error) {
        throw new Error('Could not modify group');
    }
}

exports.SetPrivateMode = async (groupId) => {
    try {
        var groupInfo = await Group.findByPk(groupId);

        if (!groupInfo) {
            throw new Error('group does not exist');
        } 

        groupInfo.PrivateMode = groupInfo.PrivateMode ? false : true;

        groupInfo.LastUpdatedAt = Date.now();

        await groupInfo.save();
    } catch (error) {
        throw new Error('Could not modify group');
    }
}

exports.AddUserJoinRequests = async (groupId, joinRequest) => {
    try {
        var groupInfo = await Group.findByPk(groupId);

        if (!groupInfo) {
            throw new Error('group does not exist');
        } 

        groupInfo.UserJoinRequests.push(joinRequest);

        groupInfo.LastUpdatedAt = Date.now();

        await groupInfo.save();
    } catch (error) {
        throw new Error('Could not modify group');
    }
}

exports.RemoveUserJoinRequests = async (groupId, joinRequest) => {
    try {
        var groupInfo = await Group.findByPk(groupId);

        if (!groupInfo) {
            throw new Error('group does not exist');
        } 

        groupInfo.UserJoinRequests.remove(joinRequest);

        groupInfo.LastUpdatedAt = Date.now();

        await groupInfo.save();
    } catch (error) {
        throw new Error('Could not modify group');
    }
}

exports.AddUserViewRequests = async (groupId, viewRequest) => {
    try {
        var groupInfo = await Group.findByPk(groupId);

        if (!groupInfo) {
            throw new Error('group does not exist');
        } 

        groupInfo.UserViewRequests.push(viewRequest);

        groupInfo.LastUpdatedAt = Date.now();

        await groupInfo.save();
    } catch (error) {
        throw new Error('Could not modify group');
    }
}

exports.RemoveUserViewRequests = async (groupId, viewRequest) => {
    try {
        var groupInfo = await Group.findByPk(groupId);

        if (!groupInfo) {
            throw new Error('group does not exist');
        } 

        groupInfo.UserViewRequests.remove(viewRequest);

        groupInfo.LastUpdatedAt = Date.now();

        await groupInfo.save();
    } catch (error) {
        throw new Error('Could not modify group');
    }
}

exports.AddGroupActivityLog = async (groupId, groupLogActivity) => {
    try {
        var groupInfo = await Group.findByPk(groupId);

        if (!groupInfo) {
            throw new Error('group does not exist');
        } 

        groupInfo.GroupActivityLog.push(groupLogActivity);

        groupInfo.LastUpdatedAt = Date.now();

        await groupInfo.save();
    } catch (error) {
        throw new Error('Could not modify group');
    }
} 

exports.AddGroupTag = async (groupId, groupTag) => {
    try {
        var groupInfo = await Group.findByPk(groupId);

        if (!groupInfo) {
            throw new Error('group does not exist');
        } 

        groupInfo.GroupTags.push(groupTag);

        groupInfo.LastUpdatedAt = Date.now();

        await groupInfo.save();
    } catch (error) {
        throw new Error('Could not modify group');
    }
}

exports.RemoveGroupTag = async (groupId, groupTag) => {
    try {
        var groupInfo = await Group.findByPk(groupId);

        if (!groupInfo) {
            throw new Error('group does not exist');
        } 

        groupInfo.GroupTags.remove(groupTag);

        groupInfo.LastUpdatedAt = Date.now();

        await groupInfo.save();
    } catch (error) {
        throw new Error('Could not modify group');
    }
}

exports.SetGroupDescription = async (groupId, groupDesc) => {
    try {
        var groupInfo = await Group.findByPk(groupId);

        if (!groupInfo) {
            throw new Error('group does not exist');
        } 

        groupInfo.GroupDescription = groupDesc;

        groupInfo.LastUpdatedAt = Date.now();

        await groupInfo.save();
    } catch (error) {
        throw new Error('Could not modify group');
    }
}

exports.DeleteGroup = async (groupId) => {
    try {
        var groupInfo = await Group.findByPk(groupId);

        if (!groupInfo) {
            throw new Error('group does not exist');
        } 

        await groupInfo.destroy();
    } catch (error) {
        throw new Error('Could not create group');
    }
}
