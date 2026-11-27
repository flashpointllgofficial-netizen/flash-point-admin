const { onValueCreated } = require("firebase-functions/v2/database");
const { initializeApp } = require("firebase-admin/app");
const { getMessaging } = require("firebase-admin/messaging");
const { getDatabase } = require("firebase-admin/database");
initializeApp();

const TOPICS = { customers: "customers", sellers: "sellers", deliveryPartners: "delivery" };

// Push delivery for every admin notification record (trusted backend).
exports.pushOnAdminNotification = onValueCreated(
  { ref: "/admin/notifications/{id}", instance: "flashpoint-57a82-default-rtdb" },
  async (event) => {
    const n = event.data.val();
    if (!n) return;
    const data = {
      campaignId: event.params.id,
      linkUrl: n.linkUrl || "",
      buttonText: n.buttonText || "",
      imageUrl: n.imageUrl || "",
    };
    const notification = { title: n.title, body: n.message };
    if (n.imageUrl) notification.imageUrl = n.imageUrl;
    const msg = getMessaging();

    if (n.audience === "SPECIFIC") {
      const snap = await getDatabase().ref(`${n.targetRole}/${n.targetUid}/fcmTokens`).get();
      const tokens = Object.keys(snap.val() || {});
      if (tokens.length) await msg.sendEachForMulticast({ tokens, notification, data });
      return;
    }
    const topics = n.audience === "EVERYONE" ? Object.values(TOPICS)
      : [{ CUSTOMERS: "customers", SELLERS: "sellers", DELIVERY: "delivery" }[n.audience]];
    await Promise.all(topics.filter(Boolean).map((topic) => msg.send({ topic, notification, data })));
  }
);
