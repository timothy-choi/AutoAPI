const UserStats = require('./UserStats');

exports.GetUserStatsById = async (userStatsId) => {
    var userStatsInfo = await UserStats.findByPk(userStatsId);

    return userStatsInfo;
}

exports.GetUserStatsByUserId = async (userId) => {
    var userStatsInfo = await UserStats.findOne({ where: { UserId: userId } });

    return userStatsInfo;
}

exports.CreateUserStats = async (userId) => {
    try {
        var userStatsInfo = GetUserStatsByUserId(userId);

        if (userStatsInfo) {
            throw new Error('account already exists');
        }

        var userStatsInstance = await UserStats.create({UserId: userId});

        return userStatsInstance;
    } catch (error) {
        throw new Error('Error with creating user stats');
    }
}

exports.DeleteUserStats = async (userStatsId) => {
    try {
        var userStatsInfo = this.GetUserStatsById(userStatsId);

        if (!userStatsInfo) {
            throw new Error('Could not find account');
        }

        await userStatsInfo.destory();
    }  catch (error) {
        throw new Error('Error with deleting user stats');
    }
}
