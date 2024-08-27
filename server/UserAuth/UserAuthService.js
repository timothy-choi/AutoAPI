const { User } = require('../models'); // Import User model from Sequelize setup
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const speakeasy = require('speakeasy');

exports.register = async (userData) => {
    const username = userData.username;

    if (username.length < 5) {
        throw new Error('Username too short');
    }

    if (!/^[a-zA-Z0-9_]+$/.test(username)) {
        throw new Error('Username must be 3-20 characters long and contain only letters, numbers, and underscores.');
    }

    const existingUser = await User.findOne({ where: { username } });
    if (existingUser) {
        throw new Error('Username is already taken.');
    }

    const hashedPassword = await bcrypt.hash(userData.password, 10);

    const mfaSecretValue = speakeasy.generateSecret({ length: 20 });

    const user = await User.create({username: userData.username, password: hashedPassword, mfaEnabled: false, mfaSecret: mfaSecretValue.base32 });
    return user;
}

exports.login = async (credentials) => {
    const user = await User.findOne({ where: { username: credentials.username } });
    if (!user || !(await bcrypt.compare(credentials.password, user.password))) {
        throw new Error("Invalid Credentials");
    }

    if (user.mfaEnabled) {
        var token = jwt.sign({ id: user.id, mfaVerified: false }, process.env.JWT_SECRET, { expiresIn: '10m' })
        return {token, mfaEnabled: user.mfaEnabled};
    }

    var token = jwt.sign({ id: user.id, mfaVerified: true }, process.env.JWT_SECRET, { expiresIn: '24h' });
    return {token, mfaEnabled: user.mfaEnabled};
}

exports.deleteUserAuth = async (userInfo) => {
    try {
        const user = await User.findOne({ where: { username: userInfo.username } });
        if (!user) {
            throw new Error("user doesn't exist");
        }

        await user.destroy();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}