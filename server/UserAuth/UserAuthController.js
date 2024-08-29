const userService = require('./UserAuthService');
const speakeasy = require('speakeasy');
const qrcode = require('qrcode');

exports.register = async (req, res) => {
    try {
        var user = await userService.register(req.body);

        const otpAuthUrl = speakeasy.otpAuthUrl({
            secret: user.mfaSecret,
            label: `Auto API (${user.username})`,
            issuer: 'Auto API'
        });

        qrcode.toDataURL(otpAuthUrl, (err, data_url) => {
            if (err) {
                throw new Error("Failed to generated QR code");
            }

            return res.status(201).json({msg: 'user registered', mfaSetupUrl: data_url});
        });
    } catch (error) {
        return res.status(400).json({ error: error.message });
    }
}

exports.login = async (req, res) => {
    try {
        const {token, mfaEnabled} = await userService.login(req.body);
        req.session.token = token;

        if (mfaEnabled) {
            return res.status(200).json({token, mfaRequired: true});
        }
        return res.status(200).json({ token });
    } catch (error) {
        return res.status(401).json({ error: error.message });
    }
}

exports.logout = async (req, res) => {
    req.session.destroy(err => {
        if (err) {
            return res.status(500).json({error: 'Failed to logout'});
        }
        res.clearCookie('connect.sid'); 
        return res.status(201).json({msg: 'user logged out'}); 
    });
}

exports.deleteUser = async (req, res) => {
    try {
        if (!req.session.token) {
            return res.status(401).json({ error: 'Not authenticated' });
        }

        const decoded = jwt.verify(req.session.token, process.env.JWT_SECRET);
        const userInfo = {
            username: decoded.username
        };

        await userService.deleteUserAuth(userInfo);

        req.session.destroy(err => {
            if (err) {
                return res.status(500).json({error: 'Failed to logout'});
            }
            res.clearCookie('connect.sid'); 
            return res.status(200).json({msg: 'account deleted successfully'}); 
        });
    } catch (error) {
        return res.status(500).json({error: 'Internal Server Error'});
    }
}

exports.replaceUsername = async (req, res) => {
    try {
        await userService.replaceUsername(req.user_id, req.username);

        return res.status(200).json({username: req.username });
    } catch (error) {
        return res.status(500).json({error: 'Internal Server Error'});
    }
}