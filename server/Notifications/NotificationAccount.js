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
    AllNotifications: {
        type: DataTypes.ARRAY(DataTypes.UUID),
        allowNull: false,
        defaultValue: [],
    },
    PushSubscription: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: {},
    }
});

module.exports = NotificationAccount;