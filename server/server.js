const express = require('express.js');
const dotenv = require('dotenv');
const session = require('express-session');
const webpush = require('web-push');
const sequelize = require('./config/postgres');
const app = express();
const userAuthRoutes = require('./UserAuth/UserAuthRouter');
const mfaRouter = require('./Mfa/MfaRouter');
const notificationAccountRouter = require('./Notifications/NotificationAccountRouter');
const searchRouter = require('./Search/SearchRouter');
const userRouter = require('./User/UserRouter');
const groupRouter = require('./Group/GroupRouter');
const userStatsRouter = require('./User/UserStatsRouter');
const notificationRouter = require('./Notifications/NotificationRouter');
const emailNotificationRouter = require('./Notifications/EmailNotificationsRouter');
const notificationWorkflowRouter = require('./Notifications/NotificationWorkflowController');

dotenv.config();
require('./config/mongodb');

app.use(express.json());
app.use(express.urlencoded({ extended: false }));

webpush.setVapidDetails(
    process.env.NOTIFICATION_EMAIL,
    process.env.PUBLIC_VAPID_KEY,
    process.env.PRIVATE_VAPID_KEY
);

app.use(session({
    secret: process.env.SESSION_SECRET,
    resave: false,
    saveUninitalized: true,
    cookie: {
        secure: true,
        maxAge: 24 * 60 * 60 * 1000
    }
}));

app.use('/userAuth', userAuthRoutes);

app.use('/mfa', mfaRouter);

app.use('/notificationAccount', notificationAccountRouter);

app.use('/search', searchRouter);

app.use('/user', userRouter);

app.use('/group', groupRouter);

app.use('/userStats', userStatsRouter);

app.use('/notification', notificationRouter);

app.use('/emailNotification', emailNotificationRouter);

app.use('/notificationWorkflow', notificationWorkflowRouter);

sequelize.sync()
    .then(() => {
        const PORT = process.env.PORT || 3000;
        app.listen(PORT, () => {});
    }).catch((err) => {});