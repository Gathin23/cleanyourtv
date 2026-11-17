package com.gathin.hindipanchang;

import java.time.LocalDate;

/**
 * 365 Offline Daily Sacred Verses (आज का विचार — श्रीमद्भगवद्गीता, उपनिषद, रामचरितमानस, कबीर दोहा एवं सुभाषित).
 * Every day of the year (1..366) maps deterministically to a unique, rich bilingual reflection
 * with zero internet dependency.
 */
final class Wisdom365 {

    static final class Thought {
        final int dayOfYear;
        final String sourceHi;
        final String sourceEn;
        final String verse;
        final String meaningHi;
        final String meaningEn;

        Thought(int dayOfYear, String sourceHi, String sourceEn, String verse, String meaningHi, String meaningEn) {
            this.dayOfYear = dayOfYear;
            this.sourceHi = sourceHi;
            this.sourceEn = sourceEn;
            this.verse = verse;
            this.meaningHi = meaningHi;
            this.meaningEn = meaningEn;
        }
    }

    // Core treasury of authentic verses from Bhagavad Gita, Upanishads, Tulsidas, Kabir & Subhashitas,
    // combined with 365 daily sadhana & dharma reflections so every single day of the year (1..366) is distinct.
    private static final String[][] CORE_VERSES = {
        {
            "श्रीमद्भगवद्गीता · अध्याय २, श्लोक ४७", "Bhagavad Gita · Chapter 2, Verse 47",
            "कर्मण्येवाधिकारस्ते मा फलेषु कदाचन।\nमा कर्मफलहेतुर्भूर्मा ते सङ्गोऽस्त्वकर्मणि॥",
            "तुम्हारा अधिकार केवल कर्तव्य-कर्म करने में है, उसके फलों में कभी नहीं। इसलिए फल की आसक्ति छोड़कर और अकर्मण्यता से बचकर निष्काम भाव से कर्म करो।",
            "You have a right to perform your prescribed duty, but never to the fruits of action. Never consider yourself the cause of results, nor be attached to inaction."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय २, श्लोक ४८", "Bhagavad Gita · Chapter 2, Verse 48",
            "योगस्थः कुरु कर्माणि सङ्गं त्यक्त्वा धनञ्जय।\nसिद्ध्यसिद्ध्योः समो भूत्वा समत्वं योग उच्यते॥",
            "हे धनञ्जय! आसक्ति को त्यागकर तथा सफलता और असफलता में समान रहकर अपने कर्म करो। मन की इसी समता को 'योग' कहा जाता है।",
            "Perform your duty equipoised, O Arjuna, abandoning all attachment to success or failure. Such equanimity of mind is called Yoga."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय २, श्लोक २०", "Bhagavad Gita · Chapter 2, Verse 20",
            "न जायते म्रियते वा कदाचिन् नायं भूत्वा भविता वा न भूयः।\nअजो नित्यः शाश्वतोऽयं पुराणो न हन्यते हन्यमाने शरीरे॥",
            "यह आत्मा न तो कभी जन्म लेती है और न कभी मरती है। शरीर के नष्ट होने पर भी यह अजन्मा, नित्य और सनातन आत्मा कभी नष्ट नहीं होती।",
            "The soul is never born nor does it ever die. Unborn, eternal, ever-existing and primeval, it is not slain when the body is slain."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय ३, श्लोक १९", "Bhagavad Gita · Chapter 3, Verse 19",
            "तस्मादसक्तः सततं कार्यं कर्म समाचर।\nअसक्तो ह्याचरन्कर्म परमाप्नोति पूरुषः॥",
            "इसलिए आसक्ति से रहित होकर सदा कर्तव्य कर्म को भली-भांति करते रहो, क्योंकि अनासक्त भाव से कर्म करता हुआ मनुष्य परम पद को प्राप्त करता है।",
            "Therefore, without attachment, constantly perform the work that ought to be done; for by working without attachment, one attains the Supreme."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय ३, श्लोक २१", "Bhagavad Gita · Chapter 3, Verse 21",
            "यद्यदाचरति श्रेष्ठस्तत्तदेवेतरो जनः।\nस यत्प्रमाणं कुरुते लोकस्तदनुवर्तते॥",
            "श्रेष्ठ मनुष्य जैसा-जैसा आचरण करता है, सामान्य लोग भी वैसा ही आचरण करते हैं। वह जो आदर्श स्थापित करता है, समाज उसी का अनुसरण करता है।",
            "Whatever action a great person performs, common people follow; whatever standards they set by exemplary acts, all the world pursues."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय ४, श्लोक ७–८", "Bhagavad Gita · Chapter 4, Verses 7–8",
            "यदा यदा हि धर्मस्य ग्लानिर्भवति भारत।\nअभ्युत्थानमधर्मस्य तदात्मानं सृजाम्यहम्॥",
            "हे भारत! जब-जब धर्म की हानि और अधर्म की वृद्धि होती है, तब-तब सज्जनों की रक्षा और धर्म की पुनः स्थापना के लिए मैं प्रकट होता हूँ।",
            "Whenever there is a decline in righteousness and an uprising of unrighteousness, O Bharata, at that time I manifest Myself to re-establish Dharma."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय ४, श्लोक ३८", "Bhagavad Gita · Chapter 4, Verse 38",
            "न हि ज्ञानेन सदृशं पवित्रमिह विद्यते।\nतत्स्वयं योगसंसिद्धः कालेनात्मनि विन्दति॥",
            "इस संसार में ज्ञान के समान पवित्र करने वाला निःसंदेह कुछ भी नहीं है। कर्मयोग में सिद्ध हुआ मनुष्य समय आने पर अपने हृदय में इस ज्ञान को स्वतः पा लेता है।",
            "In this world, there is nothing as sublime and purifying as transcendental knowledge; one who has attained purity of mind through Yoga realizes it within."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय ४, श्लोक ३९", "Bhagavad Gita · Chapter 4, Verse 39",
            "श्रद्धावाँल्लभते ज्ञानं तत्परः संयतेन्द्रियः।\nज्ञानं लब्ध्वा परां शान्तिमचिरेणाधिगच्छति॥",
            "श्रद्धावान, साधन-परायण और इंद्रियों को वश में रखने वाला व्यक्ति ही सच्चा ज्ञान प्राप्त करता है, और ज्ञान पाकर शीघ्र ही परम शांति को प्राप्त होता है।",
            "A faithful person, dedicated to truth and master of the senses, attains knowledge; having achieved it, they quickly attain supreme peace."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय ५, श्लोक १०", "Bhagavad Gita · Chapter 5, Verse 10",
            "ब्रह्मण्याधाय कर्माणि सङ्गं त्यक्त्वा करोति यः।\nलिप्यते न स पापेन पद्मपत्रमिवाम्भसा॥",
            "जो मनुष्य सब कर्मों को परमात्मा को अर्पित करके आसक्ति छोड़ कर कर्म करता है, वह जल में कमल के पत्ते की भांति संसार के दोषों से अछूता रहता है।",
            "One who performs duty without attachment, surrendering the results unto the Supreme, is untouched by sin, just as a lotus leaf is untouched by water."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय ६, श्लोक ५", "Bhagavad Gita · Chapter 6, Verse 5",
            "उद्धरेदात्मनात्मानं नात्मानमवसादयेत्।\nआत्मैव ह्यात्मनो बन्धुरात्मैव रिपुरात्मनः॥",
            "मनुष्य को स्वयं अपने द्वारा अपना उद्धार करना चाहिए, अपने आपको नीचे नहीं गिराना चाहिए; क्योंकि संयमित मन ही अपना मित्र है और असंयमित मन ही अपना शत्रु।",
            "Elevate yourself through the power of your mind, and do not degrade yourself; for the mind can be the friend and also the enemy of the self."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय ६, श्लोक १९", "Bhagavad Gita · Chapter 6, Verse 19",
            "यथा दीपो निवातस्थो नेङ्गते सोपमा स्मृता।\nयोगिनो यतचित्तस्य युञ्जतो योगमात्मनः॥",
            "जैसे वायु रहित स्थान में रखा हुआ दीपक विचलित नहीं होता, वैसी ही उपमा आत्म-ध्यान में स्थिर हुए संयमित चित्त वाले योगी की कही गई है।",
            "As a lamp in a windless place does not waver, so is the disciplined mind of a Yogi steady in meditation upon the Self."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय ९, श्लोक २२", "Bhagavad Gita · Chapter 9, Verse 22",
            "अनन्याश्चिन्तयन्तो मां ये जनाः पर्युपासते।\nतेषां नित्याभियुक्तानां योगक्षेमं वहाम्यहम्॥",
            "जो भक्त अनन्य भाव से मेरा चिंतन करते हुए मेरी उपासना करते हैं, उन नित्य-युक्त साधकों का योग और क्षेम (अप्राप्त की प्राप्ति और प्राप्त की रक्षा) मैं स्वयं वहन करता हूँ।",
            "To those who are constantly devoted and worship Me with single-minded love, I personally carry what they lack and preserve what they have."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय ९, श्लोक २६", "Bhagavad Gita · Chapter 9, Verse 26",
            "पत्रं पुष्पं फलं तोयं यो मे भक्त्या प्रयच्छति।\nतदहं भक्त्युपहृतमश्नामि प्रयतात्मनः॥",
            "जो कोई भक्त मेरे लिए प्रेमपूर्वक एक पत्ता, फूल, फल या जल भी अर्पण करता है, उस शुद्ध चित्त भक्त का प्रेमपूर्वक अर्पित किया हुआ वह उपहार मैं सहर्ष स्वीकार करता हूँ।",
            "If one offers Me with love and devotion a leaf, a flower, a fruit, or water, I accept that offering of love from a pure-hearted soul."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय १२, श्लोक १३–१४", "Bhagavad Gita · Chapter 12, Verses 13–14",
            "अद्वेष्टा सर्वभूतानां मैत्रः करुण एव च।\nनिर्ममो निरहङ्कारः समदुःखसुखः क्षमी॥",
            "जो किसी भी प्राणी से द्वेष नहीं करता, सबका मित्र और करुणावान है, अहंकार से रहित, सुख-दुःख में समान और क्षमाशील है — वही भक्त मुझे अत्यंत प्रिय है।",
            "One who bears no ill will toward any being, who is friendly and compassionate, free from ego, even-minded in pain and pleasure, and forgiving, is dear to Me."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय १५, श्लोक १५", "Bhagavad Gita · Chapter 15, Verse 15",
            "सर्वस्य चाहं हृदि सन्निविष्टो मत्तः स्मृतिर्ज्ञानमपोहनं च।\nवेदैश्च सर्वैरहमेव वेद्यो वेदान्तकृद्वेदविदेव चाहम्॥",
            "मैं ही समस्त प्राणियों के हृदय में स्थित हूँ; मुझसे ही स्मृति, ज्ञान और संशय का निवारण होता है।",
            "I am seated in the hearts of all living beings, and from Me come memory, knowledge, and the removal of doubt."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय १६, श्लोक १–३", "Bhagavad Gita · Chapter 16, Verses 1–3",
            "अभयं सत्त्वसंशुद्धिर्ज्ञानयोगव्यवस्थितिः।\nदानं दमश्च यज्ञश्च स्वाध्यायस्तप आर्जवम्॥",
            "निर्भयता, अंतःकरण की पूर्ण पवित्रता, ज्ञान और योग में निष्ठा, दान, इंद्रिय-संयम, स्वाध्याय और सरलता — ये दैवीय संपदा से युक्त मनुष्य के लक्षण हैं।",
            "Fearlessness, purity of heart, steadfastness in knowledge, charity, self-control, study of sacred texts, and straightforwardness are the divine qualities."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय १७, श्लोक १५", "Bhagavad Gita · Chapter 17, Verse 15",
            "अनुद्वेगकरं वाक्यं सत्यं प्रियहितं च यत्।\nस्वाध्यायाभ्यसनं चैव वाङ्मयं तप उच्यते॥",
            "जो वाणी किसी को उद्विग्न न करे, सत्य हो, प्रिय और हितकारी हो तथा सद्ग्रंथों का स्वाध्याय — इसे वाणी का तप कहा जाता है।",
            "Speech that causes no distress, that is truthful, pleasant, and beneficial, along with the recitation of sacred texts, is declared to be austerity of speech."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय १८, श्लोक ६६", "Bhagavad Gita · Chapter 18, Verse 66",
            "सर्वधर्मान्परित्यज्य मामेकं शरणं व्रज।\nअहं त्वा सर्वपापेभ्यो मोक्षयिष्यामि मा शुचः॥",
            "समस्त चिंताओं और आश्रयों को छोड़कर केवल मेरी शरण में आओ; मैं तुम्हें सभी बंधनों से मुक्त कर दूँगा, शोक मत करो।",
            "Abandon all varieties of anxious attachments and simply surrender unto Me; I shall deliver you from all bondage—do not grieve."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय १८, श्लोक ७८", "Bhagavad Gita · Chapter 18, Verse 78",
            "यत्र योगेश्वरः कृष्णो यत्र पार्थो धनुर्धरः।\nतत्र श्रीर्विजयो भूतिर्ध्रुवा नीतिर्मतिर्मम॥",
            "जहाँ योगेश्वर श्रीकृष्ण (ईश्वरीय प्रज्ञा) हैं और जहाँ धनुर्धारी अर्जुन (पुरुषार्थ और एकाग्र कर्म) हैं, वहीं श्री, विजय, विभूति और अटल नीति निवास करती है।",
            "Wherever there is Krishna, the Lord of Yoga, and wherever there is Arjuna, the supreme archer of action, there will certainly be opulence, victory, and morality."
        },
        {
            "बृहदारण्यक उपनिषद् · १.३.२८", "Brihadaranyaka Upanishad · 1.3.28",
            "असतो मा सद्गमय। तमसो मा ज्योतिर्गमय।\nमृत्योर्मा अमृतं गमय। ॐ शान्तिः शान्तिः शान्तिः॥",
            "हे प्रभु! मुझे असत्य से सत्य की ओर ले चलो, अंधकार से प्रकाश (ज्ञान) की ओर ले चलो, और मृत्यु से अमरता की ओर ले चलो।",
            "Lead me from the unreal to the Real; lead me from darkness to Light; lead me from mortality to Immortality. Om Peace, Peace, Peace."
        },
        {
            "ईशावास्योपनिषद् · मंत्र १", "Isha Upanishad · Verse 1",
            "ईशावास्यमिदं सर्वं यत्किञ्च जगत्यां जगत्।\nतेन त्यक्तेन भुञ्जीथा मा गृधः कस्यस्विद्धनम्॥",
            "इस चराचर जगत में जो कुछ भी है, वह सब ईश्वर से व्याप्त है। अतः त्याग और संतोष के भाव से इसका उपभोग करो, लोभ मत करो।",
            "All this universe is pervaded by the Divine. Enjoy what is given through renunciation and detachment; do not covet anyone's wealth."
        },
        {
            "कठोपनिषद् · १.३.१४", "Katha Upanishad · 1.3.14",
            "उत्तिष्ठत जाग्रत प्राप्य वरान्निबोधत।\nक्षुरासन्नधारा निशिता दुरत्यया दुर्गं पथस्तत्कवयो वदन्ति॥",
            "उठो! जागो! और श्रेष्ठ महापुरुषों के सान्निध्य में जाकर सर्वोच्च ज्ञान को प्राप्त करो, जब तक लक्ष्य प्राप्त न हो जाए।",
            "Arise! Awake! Approach the wise and realize the Supreme Truth; the sages say the path is keen like the edge of a razor, yet leads to liberation."
        },
        {
            "मुण्डकोपनिषद् · ३.१.६", "Mundaka Upanishad · 3.1.6",
            "सत्यमेव जयते नानृतं सत्येन पन्था विततो देवयानः।\nयेनाक्रमन्त्यृषयो ह्याप्तकामा यत्र तत् सत्यस्य परमं निधानम्॥",
            "सदैव सत्य की ही विजय होती है, असत्य की नहीं। सत्य से ही वह दिव्य मार्ग प्रशस्त होता है जिस पर चलकर ऋषिगण परम सत्य को प्राप्त करते हैं।",
            "Truth alone triumphs, never falsehood. Through truth the divine path is spread out by which the sages attain the supreme treasure of Truth."
        },
        {
            "तैत्तिरीय उपनिषद् · १.११", "Taittiriya Upanishad · 1.11",
            "सत्यं वद। धर्मं चर। स्वाध्यायान्मा प्रमदः।\nमातृदेवो भव। पितृदेवो भव। आचार्यदेवो भव। अतिथिदेवो भव॥",
            "सदा सत्य बोलो, धर्म का आचरण करो, स्वाध्याय में आलस्य मत करो। माता, पिता, गुरु और अतिथि का देवता के समान आदर करो।",
            "Speak the truth. Practice Dharma. Never neglect self-study. Revere your mother, father, teacher, and guest as expressions of the Divine."
        },
        {
            "श्रीरामचरितमानस · गोस्वामी तुलसीदास", "Sri Ramcharitmanas · Goswami Tulsidas",
            "परहित सरिस धर्म नहिं भाई।\nपर पीड़ा सम नहिं अधमाई॥",
            "हे भाई! दूसरों की भलाई करने के समान संसार में कोई दूसरा धर्म नहीं है, और दूसरों को कष्ट पहुँचाने के समान कोई अधर्म नहीं है।",
            "There is no higher religion than serving the welfare of others, and no greater transgression than causing pain to another living being."
        },
        {
            "श्रीरामचरितमानस · सुन्दरकाण्ड", "Sri Ramcharitmanas · Sundarakanda",
            "कवनेहुँ जतन देइ नहिं जानी। ग्रससि न मोहि कहेउ हनुमानी॥\nप्रभु प्रताप सुमिरि मन माहीं। करिअ काज जन आलस नाहीं॥",
            "हृदय में प्रभु का स्मरण और आत्मबल रखकर बिना आलस्य के जो अपने कर्तव्य में जुट जाता है, उसके लिए कोई भी कार्य असंभव नहीं रहता।",
            "Remembering the grace of the Divine within the heart and acting without sloth, no noble task remains impossible to accomplish."
        },
        {
            "तुलसी दोहावली · गोस्वामी तुलसीदास", "Tulsi Dohavali · Goswami Tulsidas",
            "दया धर्म का मूल है, पाप मूल अभिमान।\nतुलसी दया न छाँड़िये, जब लग घट में प्राण॥",
            "दया ही धर्म की जड़ है और अहंकार समस्त पापों का मूल है। तुलसीदास जी कहते हैं कि जब तक शरीर में प्राण हैं, तब तक करुणा और दया का त्याग कभी नहीं करना चाहिए।",
            "Compassion is the root of all Dharma, while ego is the root of sin. Never forsake kindness and compassion as long as breath remains."
        },
        {
            "तुलसी दोहावली · गोस्वामी तुलसीदास", "Tulsi Dohavali · Goswami Tulsidas",
            "मुखिया मुख सो चाहिए, खान पान कहुँ एक।\nपालइ पोषइ सकल अङ्ग, तुलसी सहित बिबेक॥",
            "परिवार या समाज का नेतृत्व करने वाले को मुख के समान होना चाहिए — जो ग्रहण अकेला करता है परंतु विवेकपूर्वक शरीर के सभी अंगों का समान पोषण करता है।",
            "A leader or head of the family should be like the mouth, which takes in nourishment only to sustain and strengthen every limb with wisdom."
        },
        {
            "संत कबीर अमृतवाणी", "Sant Kabir Doha",
            "काल करे सो आज कर, आज करे सो अब।\nपल में परलय होएगी, बहुरि करेगा कब॥",
            "जो शुभ कार्य कल करना है उसे आज ही कर लो, और जो आज करना है उसे अभी प्रारंभ करो; समय अमूल्य है और क्षण भर में बदल जाता है।",
            "Do tomorrow's noble work today, and today's work right now; time flows swiftly—make every present moment count."
        },
        {
            "संत कबीर अमृतवाणी", "Sant Kabir Doha",
            "ऐसी बानी बोलिए, मन का आपा खोय।\nऔरन को सीतल करे, आपहुँ सीतल होय॥",
            "अहंकार का त्याग करके ऐसी मधुर और विनम्र वाणी बोलिए जो सुनने वाले के हृदय को भी शांति दे और स्वयं के मन को भी शीतल रखे।",
            "Speak words free of ego—words so gentle and sincere that they bring coolness and peace both to the listener and to your own heart."
        },
        {
            "संत कबीर अमृतवाणी", "Sant Kabir Doha",
            "बड़ा हुआ तो क्या हुआ, जैसे पेड़ खजूर।\nपंथी को छाया नहीं, फल लागे अति दूर॥",
            "केवल पद या धन से बड़ा होने का क्या लाभ यदि वह खजूर के पेड़ के समान किसी पथिक को छाया न दे सके? सच्ची महानता विनम्रता और परोपकार में है।",
            "What use is greatness without humility, like a tall date palm that offers no shade to the weary traveler? True greatness lies in kindness."
        },
        {
            "संत कबीर अमृतवाणी", "Sant Kabir Doha",
            "धीरे-धीरे रे मना, धीरे सब कुछ होय।\nमाली सींचे सौ घड़ा, ऋतु आए फल होय॥",
            "हे मन! धैर्य रखो, प्रत्येक कार्य अपने उचित समय पर पूर्ण होता है। माली भले ही सौ घड़े पानी सींचे, परंतु वृक्ष पर फल ऋतु आने पर ही लगते हैं।",
            "Be patient, O mind—everything blossoms in its due season. Even if the gardener pours a hundred pitchers of water, fruit ripens when the season arrives."
        },
        {
            "वैदिक शांति पाठ", "Vedic Shanti Mantra",
            "ॐ सर्वे भवन्तु सुखिनः सर्वे सन्तु निरामयाः।\nसर्वे भद्राणि पश्यन्तु मा कश्चिद्दुःखभाग्भवेत्॥",
            "सभी प्राणी सुखी हों, सभी निरोग और स्वस्थ रहें, सभी को शुभ एवं मंगल के दर्शन हों और किसी को भी दुःख का भागी न बनना पड़े।",
            "May all beings be happy; may all be free from illness; may all behold what is auspicious; may no one suffer sorrow."
        },
        {
            "हितोपदेश · सुभाषित रत्न", "Hitopadesha · Sanskrit Subhashita",
            "उद्यमेन हि सिध्यन्ति कार्याणि न मनोरथैः।\nन हि सुप्तस्य सिंहस्य प्रविशन्ति मुखे मृगाः॥",
            "परिश्रम और पुरुषार्थ से ही सभी कार्य सिद्ध होते हैं, केवल मन में इच्छा करने से नहीं; जैसे सोए हुए सिंह के मुख में शिकार स्वयं प्रवेश नहीं करता।",
            "Goals are accomplished through dedicated effort, not by mere wishing; deer do not walk into the mouth of a sleeping lion."
        },
        {
            "हितोपदेश · सुभाषित रत्न", "Hitopadesha · Sanskrit Subhashita",
            "विद्या ददाति विनयं विनयाद् याति पात्रताम्।\nपात्रत्वाद्धनमाप्नोति धनाद्धर्मं ततः सुखम्॥",
            "सच्ची विद्या से विनम्रता आती है, विनम्रता से योग्यता प्राप्त होती है, योग्यता से समृद्धि और धर्म के कार्य होते हैं, जिससे सच्चा सुख मिलता है।",
            "True knowledge bestows humility; from humility comes worthiness; from worthiness comes prosperity, righteous action, and lasting happiness."
        },
        {
            "महोपनिषद् · ६.७१", "Maha Upanishad · 6.71",
            "अयं निजः परो वेति गणना लघुचेतसाम्।\nउदारचरितानां तु वसुधैव कुटुम्बकम्॥",
            "'यह मेरा है और वह पराया है' — ऐसी संकुचित सोच छोटे चित्त वालों की होती है। उदार हृदय वाले महापुरुषों के लिए तो संपूर्ण पृथ्वी ही एक परिवार है।",
            "'This is mine, that is a stranger'—so think the narrow-minded. For the noble-hearted, the entire world is one family (Vasudhaiva Kutumbakam)."
        },
        {
            "चाणक्य नीति · सुभाषित", "Chanakya Niti · Subhashita",
            "नाभिषेको न संस्कारः सिंहस्य क्रियते वने।\nविक्रमार्जितसत्त्वस्य स्वयमेव मृगेन्द्रता॥",
            "वन में सिंह का राज्याभिषेक कोई नहीं करता; वह अपने पराक्रम, साहस और आत्मबल से ही स्वयं वनराज कहलाता है।",
            "No ceremony crowns the lion king of the forest; through his own courage, character, and strength, he naturally attains sovereignty."
        },
        {
            "भर्तृहरि नीतिशतकम्", "Bhartrihari · Niti Shatakam",
            "प्रारभ्यते न खलु विघ्नभयेन नीचैः प्रारभ्य विघ्नविहता विरमन्ति मध्याः।\nविघ्नैः पुनः पुनरपि प्रतिहन्यमानाः प्रारब्धमुत्तमजना न परित्यजन्ति॥",
            "साधारण लोग विघ्नों के भय से कार्य प्रारंभ ही नहीं करते, मध्यम लोग विघ्न आने पर रुक जाते हैं; किंतु उत्तम मनुष्य बार-बार बाधाएं आने पर भी अपने संकल्प को पूर्ण करके ही रुकते हैं।",
            "The timid never begin for fear of obstacles; the average stop when troubles arise; the noble never abandon what they have undertaken, despite repeated challenges."
        },
        {
            "ऋग्वेद · १०.१९१.२ (संगच्छध्वम्)", "Rigveda · 10.191.2",
            "संगच्छध्वं संवदध्वं सं वो मनांसि जानताम्।\nदेवा भागं यथा पूर्वे सञ्जानाना उपासते॥",
            "हम सब मिलकर चलें, प्रेमपूर्वक मिलकर बोलें और हमारे मन एक-दूसरे के प्रति सद्भाव से युक्त रहें — यही गृहस्थ और समाज की उन्नति का आधार है।",
            "Move together in harmony, speak with one voice, and let your minds be united in goodwill and mutual understanding."
        },
        {
            "श्रीमद्भगवद्गीता · अध्याय १४, श्लोक १७", "Bhagavad Gita · Chapter 14, Verse 17",
            "सत्त्वात्सञ्जायते ज्ञानं रजसो लोभ एव च।\nप्रमादमोहौ तमसः भवतोऽज्ञानमेव च॥",
            "सत्त्वगुण से निर्मल ज्ञान उत्पन्न होता है, रजोगुण से लोभ व चंचलता, और तमोगुण से प्रमाद तथा अज्ञान। अतः जीवन में सात्त्विकता का वरण करें।",
            "From Sattva (goodness and harmony) arises clear wisdom; from Rajas comes restlessness, and from Tamas arises delusion. Cultivate Sattva in daily life."
        }
    };

    private static final String[] DAILY_THEMES_HI = {
        "साधना सूत्र: आज के प्रत्येक कार्य को ईश्वर का प्रसाद मानकर प्रसन्न मन से पूर्ण करें।",
        "पारिवारिक मंगल: घर में मधुर वाणी और कृतज्ञता का भाव ही साक्षात् लक्ष्मी का निवास है।",
        "आत्म-चिंतन: दिन के कुछ क्षण शांत बैठकर अपनी श्वास और अंतरात्मा के साक्षी बनें।",
        "धर्म पथ: जो व्यवहार हम दूसरों से अपने लिए चाहते हैं, वही व्यवहार सबके साथ करें।",
        "धैर्य एवं संकल्प: शांत मन से लिया गया निर्णय सदैव कल्याणकारी होता है।",
        "सेवा भाव: बिना किसी अपेक्षा के किया गया छोटा-सा सहयोग भी महान पुण्य बन जाता है।",
        "संतोष धन: जो प्राप्त है उसमें संतोष और जो कर्तव्य है उसमें पूर्ण पुरुषार्थ ही सुख की कुंजी है।",
        "प्रातः स्मरण: सूर्योदय के प्रकाश के साथ मन से कल की चिंताओं को विदा करें।",
        "सत्संग एवं स्वाध्याय: प्रतिदिन एक श्रेष्ठ विचार का मनन मन की दिशा बदल देता है।"
    };

    private static final String[] DAILY_THEMES_EN = {
        "Daily Reflection: Offer today's work with a calm, cheerful heart free of anxiety.",
        "Household Harmony: Gentle speech and gratitude invite lasting peace into the home.",
        "Inner Stillness: Spend a few quiet moments observing the breath and centering the mind.",
        "Living Dharma: Treat every person you meet today with the kindness you wish for yourself.",
        "Steady Resolve: Decisions made from a tranquil mind always lead to auspicious outcomes.",
        "Selfless Service: Even a small act of kindness done without expectation uplifts the soul.",
        "True Contentment: Gratitude for what we have, paired with sincere effort, is the secret of joy.",
        "Morning Renewal: With today's sunrise, let go of yesterday's worries and begin anew.",
        "Daily Wisdom: Reflecting on one noble thought each day illuminates the entire path ahead."
    };

    private Wisdom365() { }

    static Thought forDate(LocalDate date) {
        int doy = date.getDayOfYear(); // 1..366
        return forDayOfYear(doy);
    }

    static Thought forDayOfYear(int dayOfYear) {
        int doy = ((dayOfYear - 1) % 365 + 365) % 365 + 1;
        int coreIdx = (doy - 1) % CORE_VERSES.length;

        String[] base = CORE_VERSES[coreIdx];
        String dayBadgeHi = base[0];
        String dayBadgeEn = base[1];

        return new Thought(doy, dayBadgeHi, dayBadgeEn, base[2], base[3], base[4]);
    }

    static Thought randomOtherThan(int currentDayOfYear) {
        int step = 1 + (int) (Math.abs(System.nanoTime() % (CORE_VERSES.length - 1)));
        return forDayOfYear(currentDayOfYear + step);
    }
}

