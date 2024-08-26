const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');


const UserAuth = sequelize.define('UserAuth', {
    id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    username: {
        type: DataTypes.STRING,
        allowNull: false,
        unique: true,
    },
    password: {
        type: DataTypes.STRING,
        allowNull: false,
    },
});

module.exports = UserAuth;