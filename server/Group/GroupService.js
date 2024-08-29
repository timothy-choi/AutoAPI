const { Group } = require('../Group/Group'); 

exports.GetGroupById = async (groupId) => {
    var groupInfo = await Group.findByPk(groupId);

    return groupInfo;
}

exports.GetGroupByGroupName = async (groupName) => {
    var groupInfo = await Group.findOne({ where: { GroupName: groupName } });

    return groupInfo;
}
