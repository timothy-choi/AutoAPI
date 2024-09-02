const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const NotificationAccount = sequelize.define('NotificationAccount', {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    UserId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    NotificationsOn: {
        type: DataTypes.BOOLEAN,
        allowNull: false,
    },
});

module.exports = NotificationAccount;