module ServantMgmt
{
    interface Counter
    {
        int getValue();
        void setValue(int newValue);
        int add(int delta);
    };
};
