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
