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

        var userInfo = UserService.CreateUser(req.body);

        return res.status(201).json(userInfo);
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
