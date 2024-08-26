const userService = require('./UserAuthService');

exports.register = async (req, res) => {
    try {
        await userService.register(req.body);
        res.redirect("/login");
    } catch (error) {
        return res.status(400).json({ error: error.message });
    }
}

exports.login = async (req, res) => {
    try {
        const token = await userService.login(req.body);
        return res.status(200).json({ token });
    } catch (error) {
        return res.status(401).json({ error: error.message });
    }
}

exports.logout = async (req, res) => {
    req.session.destroy(err => {
        if (err) {
            return res.status(500).send('Failed to logout');
        }
        res.clearCookie('connect.sid'); 
        res.redirect('/login'); 
    });
}