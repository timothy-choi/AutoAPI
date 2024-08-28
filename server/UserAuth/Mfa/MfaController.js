const speakeasy = require('speakeasy');
const jwt = require('jsonwebtoken');
const UserAuth = require('../UserAuth');

exports.verifyMFA = async (req, res) => {
    try {
        const { token } = req.body;
        const user = await UserAuth.findOne({ where: { id: req.user.id } });

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