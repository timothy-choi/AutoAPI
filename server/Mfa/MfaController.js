const speakeasy = require('speakeasy');
const jwt = require('jsonwebtoken');
const UserAuth = require('../UserAuth/UserAuth');
const qrcode = require('qrcode');

exports.verifyMFA = async (req, res) => {
    try {
        const { token } = req.body;
        const user = await UserAuth.findOne({ where: { id: req.user.id } });

        if (!user) {
            return res.status(404).json({error: 'User not found'});
        }

        const verified = speakeasy.totp.verify({
            secret: user.mfaSecret,
            encoding: 'base32',
            token: token
        });

        if (!verified) {
            return res.status(401).json({ message: 'Invalid MFA token' });
        }

        const newToken = jwt.sign({ id: user.id, mfaVerified: true }, process.env.JWT_SECRET, { expiresIn: '24h' });
        req.session.token = newToken;

        return res.status(200).json({token: newToken});

    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
};

exports.enableMFA = async (req, res) => {
    try {
        const userAuth = await UserAuth.findOne({ where: { username: req.username } });

        if (!userAuth) {
            return res.status(404).json({error: 'User not found'});
        }

        const mfaSecret = speakeasy.generateSecret({ length: 20 });
        userAuth.mfaSecret = mfaSecret.base32;
        userAuth.mfaEnabled = true; 

        await userAuth.save();

        const otpAuthUrl = speakeasy.otpAuthUrl({
            secret: user.mfaSecret,
            label: `Auto API (${req.username})`,
            issuer: 'Auto API'
        });

        qrcode.toDataURL(otpAuthUrl, (err, data_url) => {
            if (err) {
                throw new Error("Failed to generated QR code");
            }

            return res.status(201).json({msg: 'user mfa enabled', mfaSetupUrl: data_url});
        });

    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
};