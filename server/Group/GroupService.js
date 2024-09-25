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
