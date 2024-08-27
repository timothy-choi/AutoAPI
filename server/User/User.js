const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const User = sequelize.define('User', {
    id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    userAuthId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    username: {
        type: DataTypes.STRING,
        allowNull: false,
        unique: true,
    },
    email: {
        type: DataTypes.STRING,
        allowNull: false,
        unique: true,
    },
    createdAt: {
        type: DataTypes.DATE,
        defaultValue: DataTypes.NOW,
        allowNull: false
    },
    LastActiveAt: {
        type: DataTypes.DATE,
        defaultValue: DataTypes.NOW,
        allowNull: false
    },
});

module.exports = User;