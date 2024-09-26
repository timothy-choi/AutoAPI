const UserStatsService = require('./UserStatsService');

exports.GetUserStatsById = async (req, res) => {
    try {
        var userStatsInfo = await UserStatsService.GetUserStatsById(req.userStatsId);

        return res.status(200).json(userStatsInfo);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.GetUserStatsByUserId = async (req, res) => {
    try {
        var userStatsInfo = await UserStatsService.GetUserStatsByUserId(req.userId);

        return res.status(200).json(userStatsInfo);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.CreateUserStats = async (req, res) => {
    try {
        var userStatsInfo = await UserStatsService.CreateUserStats(req.body.userId);

        return res.status(201).json(userStatsInfo);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.DeleteUserStats = async (req, res) => {
    try {
        await UserStatsService.DeleteUserStats(req.userId);

        return res.status(201).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}
